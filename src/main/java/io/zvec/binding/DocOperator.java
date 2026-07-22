package io.zvec.binding;

/**
 * Document DML operator.  Maps to {@code zvec_doc_operator_t} in c_api.h.
 */
public enum DocOperator {
    INSERT(0),
    UPDATE(1),
    UPSERT(2),
    DELETE(3);

    private final int code;

    DocOperator(int code) { this.code = code; }

    public int getCode() { return code; }

    public static DocOperator fromCode(int code) {
        for (DocOperator op : values()) {
            if (op.code == code) return op;
        }
        return INSERT;
    }
}
