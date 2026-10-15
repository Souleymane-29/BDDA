import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class DiskManager implements IDiskManager {

    
    private String dmDir;

    
    private int TaillePage;

    
    private int nbPages;

    
    private ArrayList<Integer> PagesLibres = new ArrayList<>();

    
    private RandomAccessFile dataFile;

    
    private boolean initialise = false;

    
    public DiskManager() {
    }

   
    
    private void checkInitialized() throws DiskManagerException {
        if (!initialise) {
            throw new DiskManagerException("DiskManager non initialisé : appelez Init d'abord");
        }
    }

    
    private int checkPage(IPageId ipid, boolean etreAllouee) throws DiskManagerException {
        if (ipid == null) {
            throw new DiskManagerException("Identifiant de page null");
        }
        // IPageId ne fournit pas getPageIdx() : on a besoin de notre PageId
        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException("Identifiant de page invalide (type "
                    + ipid.getClass().getName() + ")");
        }
        int idx = ((PageId) ipid).getPageIdx();
        if (idx < 0 || idx >= nbPages) {
            throw new DiskManagerException("Page inexistante : " + ipid);
        }
        if (etreAllouee && PagesLibres.contains(idx)) {
            throw new DiskManagerException("Page non allouée (déjà libérée) : " + ipid);
        }
        return idx;
    }

    
    @Override
    public void Init(String dmDir, int TaillePage) throws DiskManagerException {
        if (initialise) {
            throw new DiskManagerException("Init déjà appelé : le DiskManager est déjà lié au dossier "
                    + this.dmDir);
        }
        if (dmDir == null || dmDir.isEmpty()) {
            throw new DiskManagerException("Chemin de dossier invalide");
        }
        if (TaillePage <= 0) {
            throw new DiskManagerException("Taille de page invalide : " + TaillePage);
        }

        // Le dossier doit exister (sinon on le crée) et être un vrai dossier
        File dir = new File(dmDir);
        if (dir.exists() && !dir.isDirectory()) {
            throw new DiskManagerException("Ce chemin n'est pas un dossier : " + dmDir);
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DiskManagerException("Impossible de créer le dossier : " + dmDir);
        }

        
        int nvNbPages = 0;
        ArrayList<Integer> PagesLibres  = new ArrayList<>();
        File meta = new File(dir, "dm.meta");
        File data = new File(dir, "data.bin");
        boolean reuse = meta.exists();

        // Si dm.meta existe, on relit l'état précédent
        if (reuse) {
            try (Scanner sc = new Scanner(meta)) {
                int savedPageSize = sc.nextInt();
                if (savedPageSize != TaillePage) {
                    throw new DiskManagerException("Taille de page incompatible : "
                            + savedPageSize + " au lieu de " + TaillePage);
                }
                nvNbPages = sc.nextInt();
                if (nvNbPages < 0) {
                    throw new DiskManagerException("dm.meta corrompu : nombre de pages négatif");
                }
                HashSet<Integer> seen = new HashSet<>();
                while (sc.hasNext()) {
                    int free = sc.nextInt();
                    if (free < 0 || free >= nvNbPages || !seen.add(free)) {
                        throw new DiskManagerException("dm.meta corrompu : page libre invalide " + free);
                    }
                    PagesLibres.add(free);
                }
            } catch (IOException | NoSuchElementException e) {
                // NoSuchElementException couvre aussi InputMismatchException
                throw new DiskManagerException("Lecture de dm.meta impossible ou fichier corrompu", e);
            }

            // data.bin doit contenir au moins toutes les pages annoncées
            if (!data.exists() || data.length() < (long) nvNbPages * TaillePage) {
                throw new DiskManagerException("data.bin manquant ou trop court par rapport à dm.meta");
            }
        }

        // Ouverture du fichier de données
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(data, "rw");
            if (!reuse) {
                raf.setLength(0); // on repart de zéro
            }
        } catch (IOException e) {
            if (raf != null) {
                try { raf.close(); } catch (IOException ignored) { }
            }
            throw new DiskManagerException("Impossible d'ouvrir data.bin", e);
        }

        
        this.dmDir = dmDir;
        this.TaillePage = TaillePage;
        this.nbPages = nbPages;
        this.PagesLibres= PagesLibres;
        this.dataFile = raf;
        this.initialise = true;
    }

    @Override
    public void Sauvegarder() throws DiskManagerException {
        checkInitialized();
        try (PrintWriter pw = new PrintWriter(new File(dmDir, "dm.meta"))) {
            pw.println(TaillePage);
            pw.println(nbPages);
            for (int i = 0; i < PagesLibres.size(); i++) {
                pw.println(PagesLibres.get(i));
            }
            // PrintWriter n'ose pas lancer d'IOException : on teste explicitement
            if (pw.checkError()) {
                throw new DiskManagerException("Erreur d'écriture de dm.meta");
            }
        } catch (IOException e) {
            throw new DiskManagerException("Impossible d'écrire dm.meta", e);
        }
    }

    /** Alloue une page : réutilise une page libre, sinon agrandit le fichier. */
    @Override
    public IPageId AllocPage() throws DiskManagerException {
        checkInitialized();
        if (!PagesLibres.isEmpty()) {
            return new PageId(PagesLibres.remove(0));
        }
        if ((long) (nbPages + 1) * TaillePage > Long.MAX_VALUE / 2 || nbPages == Integer.MAX_VALUE) {
            throw new DiskManagerException("Nombre maximum de pages atteint");
        }
        int idx = nbPages;
        try {
            dataFile.setLength((long) (nbPages + 1) * TaillePage);
        } catch (IOException e) {
            throw new DiskManagerException("Impossible d'agrandir data.bin", e);
        }
        // On ne compte la page que si l'agrandissement a réussi
        nbPages++;
        return new PageId(idx);
    }

    /** Lit une page du disque dans le buffer. */
    @Override
    public void LirePage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        checkInitialized();
        int idx = checkPage(ipid, true);
        if (buffer == null) {
            throw new DiskManagerException("Buffer null");
        }
        if (buffer.isReadOnly()) {
            throw new DiskManagerException("Buffer en lecture seule");
        }
        if (buffer.limit() < TaillePage) {
            throw new DiskManagerException("Buffer trop petit : " + buffer.limit()
                    + " < " + TaillePage);
        }
        byte[] tab = new byte[TaillePage];
        try {
            dataFile.seek((long) idx * TaillePage);
            dataFile.readFully(tab);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur de lecture de " + ipid, e);
        }
        buffer.position(0);
        buffer.put(tab);
        buffer.position(0);
    }

    /** Écrit le contenu du buffer dans une page du disque. */
    @Override
    public void EcrirePage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        checkInitialized();
        int idx = checkPage(ipid, true);
        if (buffer == null) {
            throw new DiskManagerException("Buffer null");
        }
        if (buffer.limit() < TaillePage) {
            throw new DiskManagerException("Buffer trop petit : " + buffer.limit()
                    + " < " + TaillePage);
        }
        byte[] tab = new byte[TaillePage];
        buffer.position(0);
        buffer.get(tab);
        buffer.position(0);
        try {
            dataFile.seek((long) idx * TaillePage);
            dataFile.write(tab);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'écriture de " + ipid, e);
        }
    }

    
    @Override
    public void DeallocPage(IPageId ipid) throws DiskManagerException {
        checkInitialized();
        int idx = checkPage(ipid, true); // lève une exception si déjà libre
        PagesLibres.add(idx);
    }

    
    @Override
    public int GetNombredePagesAllouees() {
        return nbPages - PagesLibres.size();
    }

    
    @Override
    public int GetNombreDePages() {
        return nbPages;
    }
}