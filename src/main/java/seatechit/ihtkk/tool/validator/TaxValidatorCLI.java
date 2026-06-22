package seatechit.ihtkk.tool.validator;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import seatechit.ihtkk.tool.ConfigInfo;
import seatechit.ihtkk.tool.ITaxViewerException;
import seatechit.ihtkk.tool.signature.XMLSignatureValidationResult;
import seatechit.ihtkk.tool.taxdoc.HSoThue;
import seatechit.ihtkk.tool.taxdoc.HSoThueFactory;
import seatechit.ihtkk.tool.taxdoc.ITaxInvalidDocException;

/**
 * iTax Validator CLI - Command-line tool for validating tax XML files
 * exactly like iTaxViewer does, without requiring the GUI.
 * 
 * Exit codes:
 *   0 = ALL files valid
 *   1 = One or more files INVALID (structure/required fields)
 *   2 = ERROR (config/internal error)
 * 
 * Usage:
 *   java -jar itax-validator.jar <itax-home> <xml-file> [xml-file2 ...]
 *   
 *   <itax-home> = path to iTaxViewer installation directory
 *                 (containing data/, certstore/, infor/ folders)
 */
public class TaxValidatorCLI {

    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ANSI colors
    private static final String RST = "\u001B[0m";
    private static final String GRN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YLW = "\u001B[33m";
    private static final String CYN = "\u001B[36m";

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(2);
        }

        String itaxHome = new File(args[0]).getAbsolutePath();
        File homeDir = new File(itaxHome);

        // Verify iTaxViewer home directory
        if (!homeDir.exists() || !homeDir.isDirectory()) {
            System.err.println("ERROR: iTaxViewer home not found: " + itaxHome);
            System.exit(2);
        }

        // Verify required resources exist
        String[] requiredPaths = {
            "data/DMucTKhai.xml", "data/DMucTbao.xml", "data/DMucCTu.xml",
            "certstore/root", "certstore/trust",
            "infor/homepage/TaxViewHomePage.htm"
        };
        for (String req : requiredPaths) {
            if (!new File(homeDir, req).exists()) {
                System.err.println("ERROR: Required resource not found: " + req + " in " + itaxHome);
                System.exit(2);
            }
        }

        ConfigInfo config;
        String originalWd = System.getProperty("user.dir");
        PrintStream originalErr = System.err;
        try {
            System.setProperty("user.dir", itaxHome);
            System.setErr(new PrintStream(new OutputStream() {
    public void write(int b) {}
    public void write(byte[] b, int off, int len) {}
}));
            config = new ConfigInfo();
        } catch (Exception e) {
            System.err.println("ERROR: Failed to initialize configuration: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
            return;
        } finally {
            System.setErr(originalErr);
            System.setProperty("user.dir", originalWd);
        }

        // Expand args: files + directories (walk recursively for *.xml)
        List<File> filesToValidate = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            File f = new File(args[i]);
            if (!f.isAbsolute()) f = new File(homeDir, args[i]);
            if (f.isDirectory()) {
                collectXmlFiles(f, filesToValidate);
            } else if (f.exists() && f.getName().toLowerCase().endsWith(".xml")) {
                filesToValidate.add(f);
            }
        }

        int totalFiles = filesToValidate.size();
        int validCount = 0;
        int invalidCount = 0;
        int errorCount = 0;

        System.out.println();
        System.out.println(CYN + "╔══════════════════════════════════════════════════╗" + RST);
        System.out.println(CYN + "║         iTax Validator CLI v1.0                 ║" + RST);
        System.out.println(CYN + "╚══════════════════════════════════════════════════╝" + RST);
        System.out.println("iTaxViewer home: " + itaxHome);
        System.out.println("Files to validate: " + totalFiles);
        System.out.println();

        PrintStream nullStream = new PrintStream(new OutputStream() {
            public void write(int b) {}
            public void write(byte[] b, int off, int len) {}
        });

        int fileIndex = 0;
        for (File xmlFile : filesToValidate) {
            fileIndex++;

            System.out.println(CYN + "─── [" + fileIndex + "/" + totalFiles + "] " + xmlFile.getName() + " ───" + RST);

            long startTime = System.currentTimeMillis();
            System.setErr(nullStream);
            try {
                HSoThue hso = HSoThueFactory.createHSoThue(xmlFile.getAbsolutePath(), config);
                System.setErr(originalErr);
                long duration = System.currentTimeMillis() - startTime;
                printSuccess(hso, duration);
                printSignatureInfo(hso);
                validCount++;
            } catch (ITaxInvalidDocException e) {
                System.setErr(originalErr);
                long duration = System.currentTimeMillis() - startTime;
                printInvalid(xmlFile.getName(), e.getMessage(), duration);
                invalidCount++;
            } catch (ITaxViewerException e) {
                System.setErr(originalErr);
                long duration = System.currentTimeMillis() - startTime;
                printInvalid(xmlFile.getName(), e.getMessage(), duration);
                invalidCount++;
            } catch (Exception e) {
                System.setErr(originalErr);
                long duration = System.currentTimeMillis() - startTime;
                printError(xmlFile.getName(), e, duration);
                errorCount++;
            }
            System.out.println();
        }

        // Summary
        printSummary(totalFiles, validCount, invalidCount, errorCount);

        System.exit(errorCount > 0 ? 2 : (invalidCount > 0 ? 1 : 0));
    }

    private static void printSuccess(HSoThue hso, long durationMs) {
        System.out.println("  " + GRN + "✓ VALID" + RST);
        System.out.println("    Mã hồ sơ:    " + hso.getMaHSo());
        System.out.println("    Tên hồ sơ:   " + hso.getTenHSo());
        System.out.println("    Phiên bản:   " + hso.getPbanHSoXML());
        System.out.println("    Loại:        " + (hso.getLoaiHSo() != null ? hso.getLoaiHSo() : "N/A"));
        System.out.println("    View:        " + (hso.getViewMethod() != null ? 
            (hso.getViewMethod().equals("1") ? "HTML" : hso.getViewMethod().equals("2") ? "Excel" : hso.getViewMethod()) : "N/A"));
        System.out.println("    XSD Schema:  " + (hso.getXsdFile() != null ? hso.getXsdFile() : "N/A"));
        System.out.println("    XSLT:        " + (hso.getXsltFile() != null ? hso.getXsltFile() : "N/A"));
        System.out.println("    Thời gian:   " + durationMs + "ms");
    }

    @SuppressWarnings("unchecked")
    private static void printSignatureInfo(HSoThue hso) {
        Collection<XMLSignatureValidationResult> sigResults = 
            hso.getSigValidationResult();
        if (sigResults != null && !sigResults.isEmpty()) {
            System.out.println("    Chữ ký số:");
            int sigIdx = 1;
            for (XMLSignatureValidationResult sig : sigResults) {
                String statusStr;
                String statusColor;
                int status = sig.getValidStatus();
                if (status == XMLSignatureValidationResult.SIG_STATUS_GOOD) {
                    statusStr = "HỢP LỆ";
                    statusColor = GRN;
                } else if (status == XMLSignatureValidationResult.SIG_STATUS_WARNING) {
                    statusStr = "CẢNH BÁO";
                    statusColor = YLW;
                } else {
                    statusStr = "LỖI";
                    statusColor = RED;
                }
                System.out.println("      [" + sigIdx + "] " + statusColor + statusStr + RST);
                String msg = sig.getValidMessage();
                if (msg != null && !msg.isEmpty()) {
                    System.out.println("           Lý do: " + msg);
                }
                String ts = sig.getTimeStamp();
                if (ts != null && !ts.isEmpty()) {
                    System.out.println("           " + ts);
                }
                sigIdx++;
            }
        }
    }

    private static void printInvalid(String fileName, String reason, long durationMs) {
        System.out.println("  " + RED + "✗ INVALID" + RST);
        System.out.println("    File:        " + fileName);
        System.out.println("    Lý do:       " + reason);
        System.out.println("    Thời gian:   " + durationMs + "ms");
    }

    private static void printError(String fileName, Exception e, long durationMs) {
        System.out.println("  " + YLW + "! ERROR" + RST);
        System.out.println("    File:        " + fileName);
        System.out.println("    Lỗi:         " + e.getClass().getSimpleName() + ": " + e.getMessage());
        System.out.println("    Thời gian:   " + durationMs + "ms");
    }

    private static void printSummary(int total, int valid, int invalid, int errors) {
        System.out.println(CYN + "═══════════════════════════════════════════════════" + RST);
        System.out.println("  KẾT QUẢ:");
        System.out.println("    Tổng số:     " + total);
        System.out.println("    " + GRN + "Hợp lệ:      " + valid + RST);
        System.out.println("    Không hợp lệ: " + invalid);
        if (errors > 0) {
            System.out.println("    " + YLW + "Lỗi:         " + errors + RST);
        }
        System.out.println(CYN + "═══════════════════════════════════════════════════" + RST);
    }

    private static void collectXmlFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectXmlFiles(f, result);
            } else if (f.getName().toLowerCase().endsWith(".xml")) {
                result.add(f);
            }
        }
    }

    private static void printUsage() {
        System.err.println("iTax Validator CLI v1.0");
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  java -jar itax-validator.jar <itax-home> <xml-file> [xml-file2 ...]");
        System.err.println();
        System.err.println("Parameters:");
        System.err.println("  <itax-home>   Path to iTaxViewer installation directory");
        System.err.println("  <xml-file>    One or more XML tax files to validate");
        System.err.println();
        System.err.println("Exit codes:");
        System.err.println("  0 = All files valid");
        System.err.println("  1 = One or more files invalid");
        System.err.println("  2 = Configuration/Internal error");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar itax-validator.jar \"C:\\Program Files\\iTaxViewer\" sample.xml");
    }
}
