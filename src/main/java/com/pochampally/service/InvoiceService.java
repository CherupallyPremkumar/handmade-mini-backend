package com.pochampally.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pochampally.entity.Order;
import com.pochampally.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final SettingsService settingsService;

    private static final Color MAROON = new Color(139, 26, 26);
    private static final Color GOLD = new Color(212, 160, 23);
    private static final Color GREY = new Color(100, 100, 100);

    private static final int MAX_INVOICE_ITEMS = 100;

    public byte[] generateInvoicePdf(Order order) {
        if (order.getItems() != null && order.getItems().size() > MAX_INVOICE_ITEMS) {
            throw new IllegalStateException("Invoice generation limited to " + MAX_INVOICE_ITEMS + " items");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, MAROON);
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);
            Font boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
            Font smallFont = new Font(Font.HELVETICA, 8, Font.NORMAL, GREY);
            Font goldFont = new Font(Font.HELVETICA, 9, Font.NORMAL, GOLD);

            // Header
            String storeName = settingsService.get("store_name");
            if (storeName.isEmpty()) storeName = "Dhanunjaiah Handlooms";

            Paragraph header = new Paragraph(storeName, titleFont);
            header.setAlignment(Element.ALIGN_LEFT);
            doc.add(header);

            doc.add(new Paragraph("Handwoven Pochampally Ikat Sarees", goldFont));

            String gstNumber = settingsService.get("gst_number");
            if (!gstNumber.isEmpty()) {
                doc.add(new Paragraph("GSTIN: " + gstNumber, smallFont));
            }
            doc.add(new Paragraph("Gattuppal, Nalgonda District, Telangana 508253", smallFont));
            doc.add(Chunk.NEWLINE);

            // Invoice title line
            PdfPTable invoiceHeader = new PdfPTable(2);
            invoiceHeader.setWidthPercentage(100);
            invoiceHeader.setWidths(new float[]{1, 1});

            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(0);
            leftCell.addElement(new Paragraph("INVOICE", new Font(Font.HELVETICA, 16, Font.BOLD, MAROON)));
            leftCell.addElement(new Paragraph("Order: " + order.getOrderNumber(), normalFont));
            leftCell.addElement(new Paragraph("Date: " + order.getCreatedTime().toString().substring(0, 10), normalFont));
            invoiceHeader.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(0);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            var addr = order.getShippingAddress();
            rightCell.addElement(new Paragraph("Ship To:", boldFont));
            rightCell.addElement(new Paragraph(order.getCustomerName(), normalFont));
            if (addr != null) {
                rightCell.addElement(new Paragraph(addr.getOrDefault("line1", ""), normalFont));
                rightCell.addElement(new Paragraph(
                        addr.getOrDefault("city", "") + ", " +
                        addr.getOrDefault("state", "") + " - " +
                        addr.getOrDefault("pincode", ""), normalFont));
            }
            rightCell.addElement(new Paragraph(order.getCustomerPhone(), normalFont));
            invoiceHeader.addCell(rightCell);
            doc.add(invoiceHeader);
            doc.add(Chunk.NEWLINE);

            // Items table
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 0.8f, 1, 0.8f, 1.2f});

            // Header row
            String[] headers = {"Item", "Qty", "Unit Price", "GST %", "Total"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(MAROON);
                cell.setPadding(8);
                cell.setHorizontalAlignment(h.equals("Item") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
                table.addCell(cell);
            }

            // Item rows
            for (OrderItem item : order.getItems()) {
                addCell(table, item.getProductName(), normalFont, Element.ALIGN_LEFT);
                addCell(table, String.valueOf(item.getQuantity()), normalFont, Element.ALIGN_RIGHT);
                addCell(table, formatPaisa(item.getUnitPrice()), normalFont, Element.ALIGN_RIGHT);
                addCell(table, (item.getGstPct() != null ? item.getGstPct() + "%" : "5%"), normalFont, Element.ALIGN_RIGHT);
                addCell(table, formatPaisa(item.getTotalPrice()), boldFont, Element.ALIGN_RIGHT);
            }
            doc.add(table);

            // Totals
            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(50);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);

            addTotalRow(totals, "Subtotal", formatPaisa(order.getSubtotal()), normalFont, boldFont);
            addTotalRow(totals, "GST", formatPaisa(order.getGstAmount()), normalFont, boldFont);
            addTotalRow(totals, "Shipping", order.getShippingCost() == 0 ? "FREE" : formatPaisa(order.getShippingCost()), normalFont, boldFont);
            if (order.getDiscountAmount() != null && order.getDiscountAmount() > 0) {
                addTotalRow(totals, "Discount", "-" + formatPaisa(order.getDiscountAmount()), normalFont, boldFont);
            }

            // Grand total
            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL", new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE)));
            totalLabel.setBackgroundColor(MAROON);
            totalLabel.setPadding(10);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.addCell(totalLabel);

            PdfPCell totalValue = new PdfPCell(new Phrase(formatPaisa(order.getTotalAmount()), new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE)));
            totalValue.setBackgroundColor(MAROON);
            totalValue.setPadding(10);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.addCell(totalValue);

            doc.add(totals);
            doc.add(Chunk.NEWLINE);
            doc.add(Chunk.NEWLINE);

            // Footer
            doc.add(new Paragraph("Thank you for shopping with " + storeName + "!", goldFont));
            doc.add(new Paragraph("This is a computer-generated invoice and does not require a signature.", smallFont));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(230, 230, 230));
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        l.setBorder(0);
        l.setPadding(4);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setBorder(0);
        v.setPadding(4);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(v);
    }

    private String formatPaisa(long paisa) {
        return "₹" + String.format("%,.2f", paisa / 100.0);
    }
}
