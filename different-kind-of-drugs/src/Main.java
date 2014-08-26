import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class Main {


    public static void main(String[] args) throws IOException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        String url = "/node1/node2/node3/";
        System.out.println(url.substring(0, url.substring(0, url.substring(0, url.lastIndexOf('/')).lastIndexOf('/')).lastIndexOf('/') + 1));
        List<Map.Entry<String, String>> strings = new ArrayList<>();
        String activeItem = "activeItem";
        int from = 1;
        int to = 1;
        int levelPage = 3;
        String str = url;
//
//
//        if(levelPage < to){
//            for (int i = from-1; i <= levelPage; i++) {
//                str = str.substring(0, str.lastIndexOf('/'));
//                AbstractMap.SimpleEntry<String, String> e = new AbstractMap.SimpleEntry<>(activeItem+(to-i),str+'/');
//                strings.add(e);
//            }
//            for (int i = levelPage+1; i < to; i++) {
//                AbstractMap.SimpleEntry<String, String> e = new AbstractMap.SimpleEntry<>(activeItem+(to-i),"");
//                strings.add(e);
//            }
//        } else{
//            for (int i = from-1; i < to ; i++) {
//                str = str.substring(0, str.lastIndexOf('/'));
//                if("".equals(str)){
//                    break;
//                }
//                AbstractMap.SimpleEntry<String, String> e = new AbstractMap.SimpleEntry<>(activeItem+(levelPage-i),str+'/');
//                strings.add(e);
//            }

//        }
        StringTokenizer tokenizer = new StringTokenizer(url, "/");
        StringBuilder sb = new StringBuilder("/");
        int i = 0;
        while (tokenizer.hasMoreTokens()) {
            sb.append(tokenizer.nextToken());
            sb.append("/");
            i++;
            AbstractMap.SimpleEntry<String, String> e = new AbstractMap.SimpleEntry<>(activeItem+i,sb.toString());
            strings.add(e);
        }


        for (Map.Entry<String, String> entry : strings.subList(from-1,Math.min(to,levelPage))) {
            System.out.println(entry);
        }
    }
}
