
package resume_analyzer.service;

import org.springframework.stereotype.Service;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class ResumeParserService {

    public String extractResumeText(byte[] content, String extension)
            throws IOException {

        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Resume file is empty.");
        }

        if ("pdf".equalsIgnoreCase(extension)) {
            return extractPdfText(content);
        }

        if ("docx".equalsIgnoreCase(extension)) {
            return extractDocxText(content);
        }

        throw new IllegalArgumentException(
                "Only PDF and DOCX files are supported."
        );
    }

    private String extractPdfText(byte[] content) throws IOException {

        try (var document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document).trim();
        }
    }

    private String extractDocxText(byte[] content) throws IOException {

        try (
            ByteArrayInputStream input =
                    new ByteArrayInputStream(content);
            XWPFDocument document = new XWPFDocument(input);
            XWPFWordExtractor extractor =
                    new XWPFWordExtractor(document)
        ) {
            return extractor.getText().trim();
        }
    }
}
