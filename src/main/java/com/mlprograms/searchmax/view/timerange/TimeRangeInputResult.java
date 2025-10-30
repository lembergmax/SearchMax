package com.mlprograms.searchmax.view.timerange;

import com.mlprograms.searchmax.model.TimeRangeTableModel;

import java.util.Date;

public record TimeRangeInputResult(Date startDate, Date endDate, TimeRangeTableModel.Mode mode) {

    public boolean isValid() {
        return startDate != null && endDate != null && mode != null && !startDate.after(endDate);
    }

}