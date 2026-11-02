package cz.mibcon.zazitky.game.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String subtitle;

    @Column(nullable = false, columnDefinition = "text")
    private String introText;

    @Column(nullable = false, columnDefinition = "text")
    private String outroText;

    /** Spoken intro / outro (MP3, path on the web); null = text only. */
    @Column(length = 500)
    private String introAudioUrl;

    @Column(length = 500)
    private String outroAudioUrl;

    /** Picture on the game intro screen (path on the web); null = none. */
    @Column(length = 500)
    private String coverImageUrl;

    /** A game for children: puzzle letters in order, no time or penalties shown, a diploma at the end. */
    private boolean forChildren;

    /** Message after a wrong answer; null = the default one. */
    @Column(length = 300)
    private String wrongAnswerText;

    /** Recording of the message after a wrong answer; null = text only. */
    @Column(length = 500)
    private String wrongAnswerAudioUrl;

    /** Title on the diploma of a game for children, e.g. „Pomocník kronikáře“; null = none. */
    @Column(length = 100)
    private String diplomaTitle;

    /** Picture on the diploma; null = the cover picture. */
    @Column(length = 500)
    private String diplomaImageUrl;

    /** Picture at the top of the finale; null = none. */
    @Column(length = 500)
    private String outroImageUrl;

    private Integer estimatedMinutes;

    @Column(nullable = false)
    private int sessionValidityHours = 24;

    @Column(nullable = false, length = 5)
    private String language = "cs";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.DRAFT;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNo")
    private List<Station> stations = new ArrayList<>();

    public void addStation(Station station) {
        station.setGame(this);
        stations.add(station);
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getIntroText() {
        return introText;
    }

    public void setIntroText(String introText) {
        this.introText = introText;
    }

    public String getOutroText() {
        return outroText;
    }

    public void setOutroText(String outroText) {
        this.outroText = outroText;
    }

    public String getIntroAudioUrl() {
        return introAudioUrl;
    }

    public void setIntroAudioUrl(String introAudioUrl) {
        this.introAudioUrl = introAudioUrl;
    }

    public String getOutroAudioUrl() {
        return outroAudioUrl;
    }

    public void setOutroAudioUrl(String outroAudioUrl) {
        this.outroAudioUrl = outroAudioUrl;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public boolean isForChildren() {
        return forChildren;
    }

    public void setForChildren(boolean forChildren) {
        this.forChildren = forChildren;
    }

    public String getWrongAnswerText() {
        return wrongAnswerText;
    }

    public void setWrongAnswerText(String wrongAnswerText) {
        this.wrongAnswerText = wrongAnswerText;
    }

    public String getWrongAnswerAudioUrl() {
        return wrongAnswerAudioUrl;
    }

    public void setWrongAnswerAudioUrl(String wrongAnswerAudioUrl) {
        this.wrongAnswerAudioUrl = wrongAnswerAudioUrl;
    }

    public String getDiplomaTitle() {
        return diplomaTitle;
    }

    public void setDiplomaTitle(String diplomaTitle) {
        this.diplomaTitle = diplomaTitle;
    }

    public String getDiplomaImageUrl() {
        return diplomaImageUrl;
    }

    public void setDiplomaImageUrl(String diplomaImageUrl) {
        this.diplomaImageUrl = diplomaImageUrl;
    }

    public String getOutroImageUrl() {
        return outroImageUrl;
    }

    public void setOutroImageUrl(String outroImageUrl) {
        this.outroImageUrl = outroImageUrl;
    }

    /** The diploma picture, or the cover when the game has none of its own. */
    public String diplomaImageOrCover() {
        return diplomaImageUrl != null ? diplomaImageUrl : coverImageUrl;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public int getSessionValidityHours() {
        return sessionValidityHours;
    }

    public void setSessionValidityHours(int sessionValidityHours) {
        this.sessionValidityHours = sessionValidityHours;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Station> getStations() {
        return stations;
    }
}
