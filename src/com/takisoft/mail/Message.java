package com.takisoft.mail;

import com.takisoft.mail.util.Base64;
import com.takisoft.mail.util.MimeUtils;
import com.takisoft.mail.util.provider.MimeProvider;
import com.takisoft.mail.util.Utils;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class Message {

    public static enum TextType {

        PLAIN("text/plain"), HTML("text/html");
        final String mime;

        TextType(String mime) {
            this.mime = mime;
        }

        public String getMime() {
            return mime;
        }
    }

    private final List<Recipient> recipients;
    private String from;
    private String subject;
    private String text;
    private TextType textType;
    private String contentType;
    private String boundary;

    private List<Attachment> files;

    private final Base64 base64;

    public Message() {
        textType = TextType.PLAIN;
        contentType = textType.getMime();
        boundary = null;
        recipients = new ArrayList<Recipient>();

        base64 = Base64.getInstance();
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<Recipient> getRecipients() {
        return recipients;
    }

    public void addRecipient(Recipient recipient) {
        recipients.add(recipient);
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        setText(TextType.PLAIN, text);
    }

    public void setText(TextType textType, String text) {
        // TODO max line length?

//        // not needed, using base64 encode later
//        String[] lines = text.split("\n");
//        StringBuilder sb = new StringBuilder();
//        for (String line : lines) {
//            if (sb.length() > 0) {
//                sb.append('\n');
//            }
//
//            if (line.charAt(0) == '.') {
//                sb.append('.');
//            }
//            sb.append(line);
//        }
        this.textType = textType;
        this.text = text;
    }

    public boolean addFile(Attachment file) {
        if (files == null) {
            files = new ArrayList<Attachment>();
            SecureRandom sr = new SecureRandom();
            boundary = "----=_MailPart" + sr.nextInt(Integer.MAX_VALUE) + "." + sr.nextInt(Integer.MAX_VALUE);
            contentType = "multipart/mixed; boundary=\"" + boundary + "\"";
        }

        return files.add(file);
    }

    /**
     * This method works only if a {@link MimeProvider} is set before using
     * {@link Utils#setMimeProvider(com.takisoft.mail.util.MimeProvider)}.
     *
     * @param filename
     * @param file
     * @return
     * @throws IOException
     */
    public boolean addFile(String filename, File file) throws IOException {
        Attachment mailFile = new Attachment(filename, file);
        return addFile(mailFile);
    }

    /**
     * This method works only if a {@link MimeProvider} is set before using
     * {@link Utils#setMimeProvider(com.takisoft.mail.util.MimeProvider)}.
     *
     * @param file
     * @return
     * @throws IOException
     */
    public boolean addFile(File file) throws IOException {
        Attachment mailFile = new Attachment(file.getName(), file);
        return addFile(mailFile);
    }

    public boolean addFile(String filePath) throws IOException {
        return addFile(new File(filePath));
    }

    public boolean addFile(String filename, File file, String mime) throws IOException {
        Attachment mailFile = new Attachment(filename, file, mime);
        return addFile(mailFile);
    }

    public boolean addFile(File file, String mime) throws IOException {
        Attachment mailFile = new Attachment(file.getName(), file, mime);
        return addFile(mailFile);
    }

    private String createHeader(String header, String value) {
        return header + ": " + value + MailConstants.CRLF;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        if (from != null) {
            sb.append(createHeader("From", from));
        }

        StringBuilder sbRecipients = new StringBuilder();
        int n = recipients.size();
        for (int i = 0; i < n; i++) {
            Recipient rec = recipients.get(i);
            String name = rec.getName();

            if (name != null) {
                sbRecipients.append(MimeUtils.createEncodedWords(name));
                sbRecipients.append(' ');
            }

            sbRecipients.append('<');
            sbRecipients.append(rec.getAddress());
            sbRecipients.append('>');
            if (i < n - 1) {
                sbRecipients.append(',');
            }
        }

        sb.append(createHeader("To", sbRecipients.toString()));

        if (subject != null) {
            sb.append(createHeader("Subject", MimeUtils.createEncodedWords(subject)));
        }

        sb.append(createHeader("Content-Type", contentType));
        sb.append(createHeader("MIME-Version", "1.0"));
        sb.append(MailConstants.CRLF);

        if (files != null) {
            for (Attachment file : files) {
                insertBoundary(sb);
                sb.append(createHeader("Content-Type", file.getMime() + "; name=\"" + file.getName() + "\""));
                sb.append(createHeader("Content-Transfer-Encoding", "base64"));
                sb.append(createHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\""));

                sb.append(MailConstants.CRLF);
                String data = base64.encodeToString(file.getData());

                if (data.length() > MailConstants.MAX_LINE_LENGTH) {
                    int len = data.length();
                    final int partSize = MailConstants.MAX_LINE_LENGTH;

                    for (int i = 0; i < len; i += partSize) {
                        int end = Math.min(len - i, partSize);
                        sb.append(data.substring(i, i + end));
                        if (i < len - partSize) {
                            sb.append(MailConstants.CRLF);
                        }
                    }
                } else {
                    sb.append(data);
                }

                sb.append(MailConstants.CRLF);
            }
        }

        if (text != null) {

            insertBoundary(sb);
            sb.append(createHeader("Content-Type", textType.getMime() + "; charset=utf-8"));
            sb.append(createHeader("Content-Transfer-Encoding", "base64"));

            sb.append(MailConstants.CRLF);
            sb.append(base64.encodeToString(Utils.getBytes(text)));
            sb.append(MailConstants.CRLF);

        }

        insertBoundary(sb, true);
        return sb.toString();
    }

    private void insertBoundary(StringBuilder sb) {
        insertBoundary(sb, false);
    }

    private void insertBoundary(StringBuilder sb, boolean isLast) {
        if (boundary == null) {
            return;
        }

        sb.append("--");
        sb.append(boundary);
        if (isLast) {
            sb.append("--");
        }
        sb.append(MailConstants.CRLF);
    }
}
