package ru.sber.qa.llmdemo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class Test {

    private String owner;
    private String updatedBy;
    private Map<String, String> customFields;
    private Date updatedOn;
    private String precondition;
    private int majorVersion;
    private String priority;
    private Date createdOn;
    private List<String> labels;
    private String objective;
    private String component;
    private String projectKey;
    private String folder;
    private String createdBy;
    private boolean latestVersion;
    private TestScript testScript;
    private String lastTestResultStatus;
    private String name;
    private String key;
    private String status;

}
