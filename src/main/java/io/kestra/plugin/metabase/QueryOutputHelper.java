package io.kestra.plugin.metabase;

import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.metabase.models.QueryResponse;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;

public class QueryOutputHelper {

    public static List<Map<String, Object>> parseRows(QueryResponse queryResult) {
        if (queryResult == null || queryResult.getData() == null) {
            return Collections.emptyList();
        }
        return parseData(queryResult.getData().getCols(), queryResult.getData().getRows());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseRows(Map<String, Object> queryResult) {
        if (queryResult == null) {
            return Collections.emptyList();
        }

        Map<String, Object> data = (Map<String, Object>) queryResult.get("data");
        if (data == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cols = (List<Map<String, Object>>) data.get("cols");
        List<List<Object>> rows = (List<List<Object>>) data.get("rows");

        return parseData(cols, rows);
    }

    private static List<Map<String, Object>> parseData(List<Map<String, Object>> cols, List<List<Object>> rows) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (cols == null || rows == null) {
            return records;
        }

        List<String> colNames = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            Map<String, Object> col = cols.get(i);
            String name = col.get("name") != null ? col.get("name").toString() : null;
            if (name == null && col.get("display_name") != null) {
                name = col.get("display_name").toString();
            }
            if (name == null) {
                name = "col_" + i;
            }
            colNames.add(name);
        }

        for (List<Object> row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < colNames.size() && i < row.size(); i++) {
                map.put(colNames.get(i), row.get(i));
            }
            records.add(map);
        }

        return records;
    }

    public static QueryOutput processOutput(RunContext runContext, List<Map<String, Object>> rows, FetchType fetchType) throws Exception {
        if (fetchType == null) {
            fetchType = FetchType.FETCH;
        }

        long size = rows.size();
        switch (fetchType) {
            case FETCH_ONE -> {
                Map<String, Object> singleRow = rows.isEmpty() ? null : rows.get(0);
                return QueryOutput.builder()
                    .row(singleRow)
                    .size(singleRow == null ? 0L : 1L)
                    .build();
            }
            case FETCH -> {
                return QueryOutput.builder()
                    .rows(rows)
                    .size(size)
                    .build();
            }
            case STORE -> {
                File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile), FileSerde.BUFFER_SIZE)) {
                    for (Map<String, Object> row : rows) {
                        FileSerde.write(outputStream, row);
                    }
                }
                URI uri = runContext.storage().putFile(tempFile);
                return QueryOutput.builder()
                    .uri(uri)
                    .size(size)
                    .build();
            }
            case NONE -> {
                return QueryOutput.builder()
                    .size(size)
                    .build();
            }
            default -> throw new IllegalArgumentException("Unsupported FetchType: " + fetchType);
        }
    }
}
