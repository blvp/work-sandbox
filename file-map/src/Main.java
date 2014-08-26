import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

public class Main {

    public static Map<String, List<File>> allPaths(File file) throws IOException {
        Map<String, List<File>> result = new HashMap<>();
        if (file.isFile()) {
            if (result.get(file.getName()) == null) {
                List<File> files = new ArrayList<>();
                files.add(file);
                result.put(file.getName(), files);
            } else {
                result.get(file.getName()).add(file);
            }
        } else {
            for (File file1 : file.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getPath().endsWith(".html") || pathname.getPath().endsWith(".jpg") || pathname.isDirectory();
                }
            })) {
                if (file1.isDirectory()) {
                    result.putAll(allPaths(file1));
                } else {
                    if (result.get(file.getName()) == null) {
                        List<File> files = new ArrayList<>();
                        files.add(file1);
                        result.put(file.getName(), files);
                    } else {
                        result.get(file.getName()).add(file1);
                    }
                }
            }
        }
        return result;
    }


    public static void main(String[] args) throws IOException {
        String path = "C:\\Users\\admin\\javaproj\\svn\\skeleton3\\cms-release\\cms\\cms\\resources\\";
        File file = new File(path);
        Map<String, List<File>> mapping = allPaths(file);
        for (Map.Entry<String, List<File>> entry : mapping.entrySet()) {
            System.out.println(entry.getKey() + "-----" + Arrays.asList(entry.getValue()));
        }
    }
}
