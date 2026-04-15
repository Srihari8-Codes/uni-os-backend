import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

public class TestOCR {
    public static void main(String[] args) {
        String[] files = {"C:\\Users\\srike\\Desktop\\college\\Achu Doc\\12th marksheet.pdf", "C:\\Users\\srike\\Downloads\\adithya 12th marksheet.pdf"};
        for(String f : files) {
            System.out.println("--- FILE: " + f + " ---");
            try (PDDocument doc = PDDocument.load(new File(f))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                System.out.println(stripper.getText(doc));
            } catch(Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
