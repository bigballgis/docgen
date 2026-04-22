package com.docgen.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateTenantRequest {
    @NotBlank(message = "租户名称不能为空")
    private String name;

    private String description;
    private String contactEmail;
    private String status;

    // getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
