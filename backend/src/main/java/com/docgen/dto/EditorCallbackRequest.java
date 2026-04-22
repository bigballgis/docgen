package com.docgen.dto;

public class EditorCallbackRequest {
    private String fileKey;
    private String status;
    private String url;
    private Long documentId;

    // getters and setters
    public String getFileKey() { return fileKey; }
    public void setFileKey(String fileKey) { this.fileKey = fileKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
}
