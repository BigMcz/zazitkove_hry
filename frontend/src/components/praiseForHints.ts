/** Extra praise by how much help the player needed. */
export function praiseForHints(hintsUsed: number): string {
  if (hintsUsed === 0) {
    return 'A to bez jediné nápovědy — mistrovský výkon!'
  }
  if (hintsUsed <= 3) {
    return 'Nápovědu jste potřebovali jen výjimečně. Výborně!'
  }
  return 'Nápovědy pomohly, ale k cíli jste došli sami. Výborně!'
}
