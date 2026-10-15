import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        String dir = "demo_dm";
        File demoDir = new File(dir);
        if (demoDir.exists()) {
            deleteRecursively(demoDir);
        }

        DiskManager dm = new DiskManager();
        dm.Init(dir, 64);

        IPageId page1 = dm.AllocPage();
        ByteBuffer writeBuffer = ByteBuffer.allocate(64);
        writeBuffer.put("HELLO-BDDA".getBytes(StandardCharsets.UTF_8));
        writeBuffer.position(0);
        dm.EcrirePage(page1, writeBuffer);

        ByteBuffer readBuffer = ByteBuffer.allocate(64);
        dm.LirePage(page1, readBuffer);
        String content = new String(readBuffer.array(), StandardCharsets.UTF_8).trim();

        System.out.println("pageSize=" + dm.GetNombreDePages());
        System.out.println("activePages=" + dm.GetNombredePagesAllouees());
        System.out.println("read=" + content);

        dm.DeallocPage(page1);
        IPageId page2 = dm.AllocPage();
        System.out.println("reusedPage=" + page2);

        dm.Sauvegarder();
        System.out.println("demo OK");
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
