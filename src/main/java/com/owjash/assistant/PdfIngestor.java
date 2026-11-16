package com.owjash.assistant;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.util.*;

@Component
public class PdfIngestor {
    public record Parsed(int pages, List<Passage> passages) {}
    public Parsed parse(byte[] bytes, String documentId) {
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw invalid("Choose a PDF no larger than 10 MB.");
        try (var pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) throw invalid("Password-protected PDFs are not supported.");
            if (pdf.getNumberOfPages() > 150) throw invalid("The first version supports up to 150 pages per PDF.");
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var chunks = new ArrayList<Passage>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                String text = stripper.getText(pdf).replaceAll("[\\p{Zs}\\t]+", " ").trim();
                for (String part : split(text)) {
                    chunks.add(new Passage(UUID.randomUUID().toString(), documentId, page, part));
                    if (chunks.size() > 500) throw invalid("This PDF contains too much text for the first version (500 passages maximum).");
                }
            }
            if (chunks.isEmpty()) throw invalid("No readable text found. Upload a text-based PDF; scanned pages need OCR first.");
            return new Parsed(pdf.getNumberOfPages(), chunks);
        } catch (ResponseStatusException e) { throw e; }
        catch (IOException | IllegalArgumentException e) { throw invalid("The file could not be read as a PDF. Try an unencrypted, text-based PDF."); }
    }

    static List<String> split(String text) {
        if (text.isBlank()) return List.of();
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 1200, text.length());
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end);
                if (space > start + 900) end = space;
            }
            parts.add(text.substring(start, end).trim());
            if (end == text.length()) break;
            start = end - 180;
        }
        return parts;
    }
    static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
