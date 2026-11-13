package com.interview.candidate.imports;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportResult {

    private int totalRows;
    private int successRows;
    private int failedRows;
    private int createdCount;
    private int updatedCount;
    private List<RowError> errors = new ArrayList<RowError>();
}
