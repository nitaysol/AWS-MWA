import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.PDFText2HTML;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class OP {

    public static String performAction(String op, String pdfUrl) throws Exception {
        String outputFileName = "result";
        try {
            URLConnection pdfUrlCon = new URL(pdfUrl).openConnection();
            pdfUrlCon.setConnectTimeout(2500);
            pdfUrlCon.setReadTimeout(2500);
            InputStream pdfUrlStream = pdfUrlCon.getInputStream();
            System.out.println("Trying to load pdf: " + pdfUrl);
            PDDocument pdf = PDDocument.load(pdfUrlStream);
            switch (op) {
                case "ToImage":
                    PDFRenderer pdfRenderer = new PDFRenderer(pdf);
                    BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 300);
                    ImageIO.write(bim, "png", new File(outputFileName + ".png"));
                    break;
                case "ToHTML":
                case "ToText":
                    outputFileName = outputFileName + ((op.equals("ToHTML")) ? ".html" : ".txt");
                    PDFTextStripper pdfStripper = (op.equals("ToHTML")) ? new PDFText2HTML() : new PDFTextStripper();
                    pdfStripper.setStartPage(1);
                    pdfStripper.setEndPage(1);
                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFileName));
                        writer.write(pdfStripper.getText(pdf));
                        writer.close();
                    }
                    catch(Exception e) {return "ERROR_writing_to_file:_" + e.getMessage();}
                    break;
                default:
                    return "ERROR_invalid_operation_(" + op + ")_on:_" + pdfUrl;
            }

            pdf.close();
            return (op.equals("ToImage")) ? ".png" : ((op.equals("ToHTML")) ? ".html" : ".txt");

        }
        catch(IOException ex){return "ERROR_could_not_open_file:_" + pdfUrl +" " +ex.getMessage();}
        catch(Exception ex){return "ERROR_an_unknown_error_as_occurred_on:" + op + "_" + pdfUrl + "_" + ex.getMessage();}
    }
}
