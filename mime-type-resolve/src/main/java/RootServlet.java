
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class RootServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("I'm here and i have already set content-type");
        BufferedReader reader;
        if (req.getRequestURI().lastIndexOf(".") != -1) {
            reader = new BufferedReader(new FileReader(new File("C:\\Users\\admin\\javaproj\\ideaproj\\blvp\\mime-type-resolve\\src\\main\\webapp\\WEB-INF" + req.getPathInfo())));
        } else {
            reader = new BufferedReader(new FileReader(new File("C:\\Users\\admin\\javaproj\\ideaproj\\blvp\\mime-type-resolve\\src\\main\\webapp\\WEB-INF/main.html")));
        }
        PrintWriter writer = resp.getWriter();
        String line;
        while ((line = reader.readLine()) != null) {
            writer.print(line);
        }
        writer.close();
    }
}
