package com.project.moabuja.dto.response.record;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class DayListResponseDto {

/**
 * [List] dayRecords : {
 * [enum]recordType(비고 기록),
 * [LocalDateTime] recordDate(날짜),
 * [string] memos(메모),
 * [int] recordAmount(금액) }
 */
    private List<DayRecordResponseDto> dayRecordList;
    private int dayIncomeAmount;
    private int dayExpenseAmount;
    private int dayChallengeAmount;
    private int dayGroupAmount;
    private int wallet;
    private int totalAmount;

    public DayListResponseDto(List<DayRecordResponseDto> dayRecordList, int dayIncomeAmount, int dayExpenseAmount, int dayChallengeAmount, int dayGroupAmount, int wallet, int totalAmount) {
        this.dayRecordList = dayRecordList;
        this.dayIncomeAmount = dayIncomeAmount;
        this.dayExpenseAmount = dayExpenseAmount;
        this.dayChallengeAmount = dayChallengeAmount;
        this.dayGroupAmount = dayGroupAmount;
        this.wallet = wallet;
        this.totalAmount = totalAmount;
    }
}