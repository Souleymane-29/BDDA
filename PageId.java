/**
 * Implémentation de IPageId.
 *
 * Une page est identifiée par un seul entier : sa position dans le
 * fichier data.bin. Le DiskManager s'en sert pour savoir à quel
 * endroit du fichier lire ou écrire (offset = pageIdx * pageSize).
 *
 * Deux PageId sont égaux s'ils ont le même numéro de page.
 */
public class PageId implements IPageId {

    // Numéro de la page dans le fichier de données
    private final int pageIdx;

    /**
     * Constructeur.
     * @param pageIdx le numéro de la page
     */
    public PageId(int pageIdx) {
        this.pageIdx = pageIdx;
    }

    /** Retourne le numéro de la page. */
    public int getPageIdx() {
        return pageIdx;
    }

    /** Égalité par valeur : même numéro de page. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PageId)) {
            return false;
        }
        return pageIdx == ((PageId) obj).pageIdx;
    }

    
    @Override
    public int hashCode() {
        return Integer.hashCode(pageIdx);
    }

    
    @Override
    public String toString() {
        return "PageId(" + pageIdx + ")";
    }
}