import java.io.File;

public class Main {




    public static void main(String[] args) {
        File file = new File("test.txt");
        file.renameTo(new File("backup/"+file.getName()+".bac"));
        System.out.println("yupi!");
    }
}
