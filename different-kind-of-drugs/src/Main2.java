import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public class Main2 {
    public static void main(String[] args) {
        GregorianCalendar gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.set(GregorianCalendar.YEAR, 1);
        DateFormat dateFormat = new SimpleDateFormat("y");
        System.out.println(dateFormat.format(gregorianCalendar.getTime()));
    }
}
