package com.project.moabuja.service;

import com.project.moabuja.domain.alarm.Alarm;
import com.project.moabuja.domain.alarm.AlarmDetailType;
import com.project.moabuja.domain.friend.Friend;
import com.project.moabuja.domain.goal.*;
import com.project.moabuja.domain.member.Member;
import com.project.moabuja.domain.record.Record;
import com.project.moabuja.domain.record.RecordType;
import com.project.moabuja.dto.request.alarm.GoalAlarmRequestDto;
import com.project.moabuja.dto.request.alarm.GoalAlarmSaveDto;
import com.project.moabuja.dto.request.goal.CreateChallengeRequestDto;
import com.project.moabuja.dto.request.goal.WaitingGoalSaveDto;
import com.project.moabuja.dto.response.goal.*;
import com.project.moabuja.exception.ErrorException;
import com.project.moabuja.model.ChallengeAcceptResponse;
import com.project.moabuja.model.ChallengeExitResponse;
import com.project.moabuja.model.ChallengePostResponse;
import com.project.moabuja.model.ChallengeRefuseResponse;
import com.project.moabuja.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.project.moabuja.domain.alarm.AlarmDetailType.*;
import static com.project.moabuja.domain.alarm.AlarmType.CHALLENGE;
import static com.project.moabuja.exception.ErrorCode.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor

public class ChallengeGoalServiceImpl implements ChallengeGoalService{

    private final MemberRepository memberRepository;
    private final ChallengeGoalRepository challengeGoalRepository;
    private final RecordRepository recordRepository;
    private final FriendRepository friendRepository;
    private final AlarmRepository alarmRepository;
    private final WaitingGoalRepository waitingGoalRepository;
    private final MemberWaitingGoalRepository memberWaitingGoalRepository;

    @Transactional
    @Override
    public void save(CreateChallengeRequestDto createChallengeRequestDto, Member currentMemberTemp) {

        Member currentMember = Optional.of(memberRepository.findById(currentMemberTemp.getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

        ChallengeGoal challengeGoal = new ChallengeGoal(createChallengeRequestDto.getCreateChallengeName(), createChallengeRequestDto.getCreateChallengeAmount(), 0);

        if (Optional.ofNullable(createChallengeRequestDto.getChallengeFriends()).isEmpty()) {
            ChallengeGoal savedGoal = challengeGoalRepository.save(challengeGoal);
            savedGoal.addMember(currentMember);
        }

        for(String name :createChallengeRequestDto.getChallengeFriends()){
            Member member = Optional.of(memberRepository.findMemberByNickname(name)).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));
            challengeGoal.addMember(member);
        }

        //member랑 challengegoal 연관관계 맺음
        ChallengeGoal savedGoal = challengeGoalRepository.save(challengeGoal);
        savedGoal.addMember(currentMember);
    }

    @Override
    public ResponseEntity<ChallengeResponseDto> getChallengeInfo(Member currentMemberTemp) {

        //여기는 프록시 생명주기 문제 땜에 필요
        Member currentMember = Optional.of(memberRepository.findById(currentMemberTemp.getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

        Optional<ChallengeGoal> challengeGoal = Optional.ofNullable(currentMember.getChallengeGoal());

        List<String> challengeDoneGoalNames = makeChallengeDoneNames(currentMember);
        List<MemberWaitingGoal> memberWaitingGoals = currentMember.getMemberWaitingGoals();

        //challengeGoal 있을때
        if (challengeGoal.isPresent()) {
            String goalStatus = "goal";

            List<ChallengeMemberDto> challengeMembers = makeChallengeMembers(challengeGoal);

            List<Record> challengeRecords = recordRepository.findRecordsByRecordTypeAndMember(RecordType.challenge, currentMember);

            List<ChallengeListDto> challengeLists = challengeRecords.stream().map(record -> {
                return new ChallengeListDto(record.getRecordDate(), record.getMemo(), record.getRecordAmount());
            }).collect(Collectors.toList());

            ChallengeResponseDto goalResponseDto = ChallengeResponseDto.builder()
                    .goalStatus(goalStatus)
                    .challengeMembers(challengeMembers)
                    .challengeName(challengeGoal.get().getChallengeGoalName())
                    .challengeDoneGoals(challengeDoneGoalNames)
                    .challengeLists(challengeLists)
                    .waitingGoals(null)
                    .build();

            return new ResponseEntity<>(goalResponseDto, HttpStatus.OK);

        } else {//challenge 없을때
            if (! memberWaitingGoals.isEmpty()) { //수락대기중
                String goalStatus = "waiting";

                List<WaitingGoalResponseDto> waitingGoals = makeWaitingGoals(memberWaitingGoals);

                ChallengeResponseDto waitingResponseDto = ChallengeResponseDto.builder()
                        .goalStatus(goalStatus)
                        .challengeMembers(null)
                        .challengeName(null)
                        .challengeDoneGoals(challengeDoneGoalNames)
                        .challengeLists(null)
                        .waitingGoals(waitingGoals)
                        .build();

                return new ResponseEntity<>(waitingResponseDto, HttpStatus.OK);

            } else { //challengeGoal 없을때
                String goalStatus = "noGoal";
                ChallengeResponseDto noGoalResponseDto = ChallengeResponseDto.builder()
                        .goalStatus(goalStatus)
                        .challengeMembers(null)
                        .challengeName(null)
                        .challengeDoneGoals(challengeDoneGoalNames)
                        .challengeLists(null)
                        .waitingGoals(null)
                        .build();

                return new ResponseEntity<>(noGoalResponseDto, HttpStatus.OK);
            }
        }
    }

    @Override
    public ResponseEntity<CreateChallengeResponseDto> getChallengeMemberCandidates(Member currentMember) {

        List<Friend> friends = friendRepository.findFriendsByMember(currentMember);
        List<CreateChallengeMemberDto> challengeMembers = new ArrayList<>();

        if (friends.size() == 0){
            CreateChallengeResponseDto createChallengeResponseDto = new CreateChallengeResponseDto(challengeMembers);
            return new ResponseEntity<>(createChallengeResponseDto, HttpStatus.OK);
        }

        for(Friend friend : friends){
            //친구의 챌린지 골을 확인
            Member friendById = Optional.of(memberRepository.findById(friend.getFriend().getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

            Optional<ChallengeGoal> friendChallengeGoal = Optional.ofNullable(friendById.getChallengeGoal());

            //이미 진행중인 챌린지 있음
            if(friendChallengeGoal.isPresent()) {
                challengeMembers.add(new CreateChallengeMemberDto(friendById.getNickname(), false, friendById.getHero()));
            } else { // 진행 중인 챌린지 없음
                challengeMembers.add(new CreateChallengeMemberDto(friendById.getNickname(),true,friendById.getHero()));
            }
        }
        CreateChallengeResponseDto challengeResponseDto = new CreateChallengeResponseDto(challengeMembers);
        return new ResponseEntity<>(challengeResponseDto, HttpStatus.OK);
    }

    @Transactional
    @Override
    public ResponseEntity<ChallengePostResponse> postChallenge(Member currentMember, GoalAlarmRequestDto goalAlarmRequestDto) {

        if (Optional.ofNullable(goalAlarmRequestDto.getFriendNickname()).isEmpty()) {
            CreateChallengeRequestDto createChallengeRequestDto = new CreateChallengeRequestDto(goalAlarmRequestDto.getGoalName(), goalAlarmRequestDto.getGoalAmount(), null);
            save(createChallengeRequestDto, currentMember);
            return new ResponseEntity<>(new ChallengePostResponse(), HttpStatus.OK);
        }

        WaitingGoal waitingGoal = waitingGoalRepository.save(WaitingGoalSaveDto.toEntity(goalAlarmRequestDto.getGoalName(), goalAlarmRequestDto.getGoalAmount(), GoalType.CHALLENGE));
        GroupGoalServiceImpl.inviteFriends(currentMember, goalAlarmRequestDto, waitingGoal, memberWaitingGoalRepository, memberRepository, alarmRepository, CHALLENGE);

        return new ResponseEntity<>(new ChallengePostResponse(), HttpStatus.OK);
    }

    @Transactional
    @Override
    public ResponseEntity<ChallengeAcceptResponse> postChallengeAccept(Member currentMemberTemp, Long alarmId) {
        Alarm alarm = Optional.of(alarmRepository.findById(alarmId)).get().orElseThrow(() -> new ErrorException(ALARM_NOT_EXIST));

        Member currentMember = Optional.of(memberRepository.findById(currentMemberTemp.getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

        WaitingGoal waitingGoal = waitingGoalRepository.findWaitingGoalById(alarm.getWaitingGoalId());
        MemberWaitingGoal currentMemberWaitingGoal = memberWaitingGoalRepository.findMemberWaitingGoalByMemberAndWaitingGoal(currentMember, waitingGoal);
        currentMemberWaitingGoal.changeIsAcceptedGoal();

        List<MemberWaitingGoal> friends = memberWaitingGoalRepository.findMemberWaitingGoalsByWaitingGoal(waitingGoal);

        // 전체 수락 전
        if (! checkAccepted(friends)) {

            List<String> friendListTmp = new ArrayList<>();
            sendChallengeAlarm(friends, friendListTmp, currentMember, accept, waitingGoal);

            alarmRepository.delete(alarm);
        }

        // 전체 수락 후 마지막 수락
        else if (checkAccepted(friends)) {

            List<String> friendListTmp = new ArrayList<>();
            List<String> friendList = sendChallengeAlarm(friends, friendListTmp, currentMember, create, waitingGoal);

            // ChallengeGoal 생성
            CreateChallengeRequestDto createChallengeRequestDto = new CreateChallengeRequestDto(waitingGoal.getWaitingGoalName(), waitingGoal.getWaitingGoalAmount(), friendList);
            save(createChallengeRequestDto, currentMember);
            waitingGoalRepository.delete(waitingGoal);
            alarmRepository.delete(alarm);
        }

        return new ResponseEntity<>(new ChallengeAcceptResponse(), HttpStatus.OK);
    }

    @Transactional
    @Override
    public ResponseEntity<ChallengeRefuseResponse> postChallengeRefuse(Member currentMemberTemp, Long alarmId) {

        Alarm alarm = Optional.of(alarmRepository.findById(alarmId)).get().orElseThrow(() -> new ErrorException(ALARM_NOT_EXIST));

        Member currentMember = Optional.of(memberRepository.findById(currentMemberTemp.getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

        WaitingGoal waitingGoal = waitingGoalRepository.findWaitingGoalById(alarm.getWaitingGoalId());
        List<MemberWaitingGoal> friends = memberWaitingGoalRepository.findMemberWaitingGoalsByWaitingGoal(waitingGoal);

        List<String> friendListTmp = new ArrayList<>();
        sendChallengeAlarm(friends, friendListTmp, currentMember, boom, waitingGoal);

        alarmRepository.delete(alarm);
        waitingGoalRepository.delete(waitingGoal);

        return new ResponseEntity<>(new ChallengeRefuseResponse(), HttpStatus.OK);
    }

    @Override
    @Transactional
    public ResponseEntity<ChallengeExitResponse> exitChallenge(Member currentMemberTemp, Long id) {

        Member currentMember = Optional.of(memberRepository.findById(currentMemberTemp.getId())).get().orElseThrow(() -> new ErrorException(MEMBER_NOT_FOUND));

        List<Member> memberList = currentMember.getChallengeGoal().getMembers();

        ChallengeGoal challengeGoal = currentMember.getChallengeGoal();
        currentMember.changeGroupGoal(null);

        if (memberList.size() == 1) { challengeGoalRepository.delete(challengeGoal); }

        return new ResponseEntity<>(new ChallengeExitResponse(), HttpStatus.OK);
    }

    @Override
    @Transactional
    public ResponseEntity<ChallengeExitResponse> exitWaitingChallenge(Long id) {

        WaitingGoal waitingGoalById = Optional.of(waitingGoalRepository.findWaitingGoalById(id)).orElseThrow(() -> new ErrorException(GOAL_NOT_EXIST));
        waitingGoalRepository.delete(waitingGoalById);

        return new ResponseEntity<>(new ChallengeExitResponse(), HttpStatus.OK);
    }

    public List<String> sendChallengeAlarm(List<MemberWaitingGoal> friends, List<String> friendList,Member currentMember, AlarmDetailType alarmDetailType, WaitingGoal waitingGoal){
        for (MemberWaitingGoal friend : friends) {
            if (friend.getMember() != currentMember) {
                friendList.add(friend.getMember().getNickname());
                GoalAlarmSaveDto alarmSaveDto = GoalAlarmSaveDto.builder()
                        .alarmType(CHALLENGE)
                        .alarmDetailType(alarmDetailType)
                        .goalName(waitingGoal.getWaitingGoalName())
                        .goalAmount(waitingGoal.getWaitingGoalAmount())
                        .waitingGoalId(waitingGoal.getId())
                        .friendNickname(currentMember.getNickname())
                        .member(friend.getMember())
                        .build();
                alarmRepository.save(GoalAlarmSaveDto.goalToEntity(alarmSaveDto));
            }
        }
        return friendList;
    }

    public boolean checkAccepted(List<MemberWaitingGoal> memberWaitingGoals) {
        boolean result = true;

        for (MemberWaitingGoal memberWaitingGoal : memberWaitingGoals) {
            if (! memberWaitingGoal.isAcceptedGoal()) {
                result = false;
                break; }}
        return result;
    }

    @NotNull
    private List<String> makeChallengeDoneNames(Member currentMember) {
        List<String> challengeDoneGoalNames = new ArrayList<>();
        for (DoneGoal doneGoal : currentMember.getDoneGaols()) {
            if (doneGoal.getGoalType() == GoalType.CHALLENGE) {
                challengeDoneGoalNames.add(doneGoal.getDoneGoalName());
            }
        }
        return challengeDoneGoalNames;
    }

    @NotNull
    private List<ChallengeMemberDto> makeChallengeMembers(Optional<ChallengeGoal> challengeGoal) {
        List<ChallengeMemberDto> challengeMembers = new ArrayList<>();
        for (Member user : challengeGoal.get().getMembers()) {
            int currentAmount = 0;
            List<Record> records = recordRepository.findRecordsByRecordTypeAndMember(RecordType.challenge, user);
            for (Record record : records) {
                currentAmount += record.getRecordAmount();
            }

            int leftAmount = challengeGoal.get().getChallengeGoalAmount() - currentAmount;
            int percent = (int) (((double) currentAmount / (double) (challengeGoal.get().getChallengeGoalAmount())) * 100);

            challengeMembers.add(ChallengeMemberDto.builder()
                    .challengeMemberNickname(user.getNickname())
                    .challengeMemberHero(user.getHero())
                    .challengeMemberLeftAmount(leftAmount)
                    .challengeMemberNowPercent(percent)
                    .build());
        }
        return challengeMembers;
    }

    @NotNull
    private List<WaitingGoalResponseDto> makeWaitingGoals(List<MemberWaitingGoal> memberWaitingGoals) {
        List<WaitingGoalResponseDto> waitingGoals = new ArrayList<>();
        for (MemberWaitingGoal memberWaitingGoal : memberWaitingGoals) {
            waitingGoals.add(new WaitingGoalResponseDto(memberWaitingGoal.getWaitingGoal().getId(),memberWaitingGoal.getWaitingGoal().getWaitingGoalName()));
        }
        return waitingGoals;
    }


}
