"""Joins MP3 recordings and pauses into one file without re-encoding (no ffmpeg needed).

All parts must have the same format (the recordings of a game are converted the same way, e.g. MPEG-1 Layer III
40 kbps 32 kHz mono, constant bit rate). ID3 tags and Xing/Info frames of the parts are left out, so the player
computes the length from the constant bit rate. A pause is made of silent frames of the same format.

Usage: python tools/audio/join_mp3.py OUT PART...   where PART is an MP3 file or ticho:SECONDS
  e.g. python tools/audio/join_mp3.py out.mp3 uvod.mp3 ticho:1 kun.mp3 ticho:1 otazka.mp3
"""
import sys

BITRATES = [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320]
SAMPLE_RATES = [44100, 48000, 32000]
SAMPLES_PER_FRAME = 1152


def frames(data, name):
    """MPEG-1 Layer III frames of the file as (header, frame bytes); without ID3 tags and the Xing/Info frame."""
    i = 0
    if data[:3] == b'ID3':
        i = 10 + ((data[6] & 0x7f) << 21 | (data[7] & 0x7f) << 14 | (data[8] & 0x7f) << 7 | (data[9] & 0x7f))
    result = []
    while i + 4 <= len(data):
        if data[i:i + 3] == b'TAG':
            break
        if data[i] != 0xff or (data[i + 1] & 0xfe) != 0xfa:
            sys.exit(f'{name}: not an MPEG-1 Layer III frame at byte {i}')
        bitrate = BITRATES[data[i + 2] >> 4]
        sample_rate = SAMPLE_RATES[(data[i + 2] >> 2) & 3]
        size = 144 * bitrate * 1000 // sample_rate + ((data[i + 2] >> 1) & 1)
        frame = data[i:i + size]
        if b'Xing' not in frame[:64] and b'Info' not in frame[:64]:
            result.append(frame)
        i += size
    return result


def format_of(frame):
    """Version, layer, bit rate, sample rate and channel mode; padding and flags may differ."""
    return frame[1] & 0xfe, frame[2] & 0xfc, frame[3] & 0xc0


def silent_frame(sample):
    """A frame with an empty side info and no main data decodes to silence."""
    header = bytes([sample[0], sample[1] | 0x01, sample[2] & 0xfd, sample[3]])  # no CRC, no padding
    bitrate = BITRATES[sample[2] >> 4]
    sample_rate = SAMPLE_RATES[(sample[2] >> 2) & 3]
    return header + bytes(144 * bitrate * 1000 // sample_rate - 4)


def main(out, parts):
    files = {part: frames(open(part, 'rb').read(), part) for part in parts if not part.startswith('ticho:')}
    sample = next(iter(files.values()))[0]
    for name, file_frames in files.items():
        if any(format_of(frame) != format_of(sample) for frame in file_frames):
            sys.exit(f'{name}: a different format than {next(iter(files))}, convert it the same way')
    sample_rate = SAMPLE_RATES[(sample[2] >> 2) & 3]

    joined = bytearray()
    for part in parts:
        if part.startswith('ticho:'):
            count = round(float(part[6:]) * sample_rate / SAMPLES_PER_FRAME)
            joined += silent_frame(sample) * count
        else:
            joined += b''.join(files[part])
    open(out, 'wb').write(joined)
    seconds = len(joined) * 8 / (BITRATES[sample[2] >> 4] * 1000)
    print(out, len(joined) // 1024, 'kB', round(seconds, 1), 's')


if __name__ == '__main__':
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2:])
