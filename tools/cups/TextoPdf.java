import java.io.File;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Imprime el texto de un PDF y su número de páginas (para las pruebas de punta a punta). */
public class TextoPdf {
    public static void main(String[] args) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File(args[0]))) {
            System.out.println("PAGINAS=" + doc.getNumberOfPages());
            System.out.println(new PDFTextStripper().getText(doc));
        }
    }
}
