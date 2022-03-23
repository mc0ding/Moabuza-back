package com.project.moabuja.service;

import com.project.moabuja.domain.friend.Friend;
import com.project.moabuja.domain.goal.DoneGoal;
import com.project.moabuja.domain.goal.GoalType;
import com.project.moabuja.domain.goal.GroupGoal;
import com.project.moabuja.domain.member.Member;
import com.project.moabuja.domain.record.Record;
import com.project.moabuja.domain.record.RecordType;
import com.project.moabuja.dto.request.goal.CreateGroupRequestDto;
import com.project.moabuja.dto.response.goal.*;
import com.project.moabuja.repository.FriendRepository;
import com.project.moabuja.repository.GroupGoalRepository;
import com.project.moabuja.repository.MemberRepository;
import com.project.moabuja.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GroupGoalServiceImpl implements GroupGoalService{

    private final MemberRepository memberRepository;
    private final GroupGoalRepository groupGoalRepository;
    private final RecordRepository recordRepository;
    private final FriendRepository friendRepository;

    @Override
    @Transactional
    public ResponseEntity<String> save(CreateGroupRequestDto groupRequestDto, Member current) {

        Optional<Member> currentUserTmp = memberRepository.findById(current.getId());
        Member currentUser = currentUserTmp.get();

        GroupGoal groupGoal = new GroupGoal(groupRequestDto.getCreateGroupName(), groupRequestDto.getCreateGroupAmount(), 0, false);

        //member랑 groupGoal 연관관계 맺음
        GroupGoal savedGoal = groupGoalRepository.save(groupGoal);
        /**
         * 여기
         */
        System.out.println(savedGoal.getId());
        Optional<GroupGoal> goal = groupGoalRepository.findById(savedGoal.getId());
        /**
         * 여기
         */
        System.out.println(currentUser.getId());
        goal.get().addMember(currentUser);

        for(String name :groupRequestDto.getGroupFriends()){
            Optional<Member> memberByNickname = memberRepository.findMemberByNickname(name);
            goal.get().addMember(memberByNickname.get());
        }
        return ResponseEntity.ok().body("같이해부자 생성 완료");
    }

    @Override
    public ResponseEntity<GroupResponseDto> getGroupInfo(Member current) {

        Optional<Member> currentUserTmp = memberRepository.findById(current.getId());
        Member currentUser = currentUserTmp.get();

        Optional<GroupGoal> groupGoal = Optional.ofNullable(currentUser.getGroupGoal());
        List<String> groupDoneGoalNames = new ArrayList<>();
        for(DoneGoal doneGoal:currentUser.getDoneGaols()){
            if(doneGoal.getGoalType() == GoalType.GROUP){
                groupDoneGoalNames.add(doneGoal.getDoneGoalName());
            }
        }

        //GroupGoal 있을때
        if (groupGoal.isPresent()){
            if(groupGoal.get().isAcceptedGroup()){

                String goalStatus = "goal";

                List<GroupMemberDto> groupMembers = new ArrayList<>();
                List<GroupListDto> groupList = new ArrayList<>();
                List<Member> members = groupGoal.get().getMembers();
                int currentAmount = 0;
                for (Member member : members) {
                    groupMembers.add(new GroupMemberDto(member.getNickname(), member.getHero()));

                    List<Record> memberGroupRecord = recordRepository.findRecordsByRecordTypeAndMember(RecordType.group, member);
                    int tmpAmount = 0;
                    for (Record record : memberGroupRecord) {
                        groupList.add(new GroupListDto(record.getRecordDate(), member.getHero(), member.getNickname(),record.getMemo(),record.getRecordAmount()));
                        tmpAmount = tmpAmount+record.getRecordAmount();
                    }
                    currentAmount += tmpAmount;
                }
                int leftAmount = groupGoal.get().getGroupGoalAmount() - currentAmount;
                int percent = (int) (((double) currentAmount / (double) (groupGoal.get().getGroupGoalAmount())) * 100);

                GroupResponseDto haveGoal = new GroupResponseDto(groupGoal.get().getId(),goalStatus,groupMembers,groupGoal.get().getGroupGoalName(),leftAmount,percent,groupDoneGoalNames,groupList);
                return ResponseEntity.ok().body(haveGoal);

            }
            else{//수락대기중
                String goalStatus = "waiting";
                GroupResponseDto waiting = new GroupResponseDto(groupGoal.get().getId(),goalStatus,null,null,0,0,groupDoneGoalNames,null);
                return ResponseEntity.ok().body(waiting);
            }
        }
        //challengeGoal 없을때
        else{
            String goalStatus = "noGoal";
            GroupResponseDto noGoal = new GroupResponseDto(null,goalStatus,null,null,0,0,groupDoneGoalNames,null);
            return ResponseEntity.ok().body(noGoal);
        }
    }

    @Override
    public ResponseEntity<CreateGroupResponseDto> getGroupMemberCandidates(Member currentUser) {

        List<Friend> friends = friendRepository.findFriendsByMember(currentUser);
        List<CreateGroupMemberDto> groupMembers = new ArrayList<>();

        if (friends.size() == 0){
            CreateGroupResponseDto createGroupResponseDto = new CreateGroupResponseDto(groupMembers);
            return ResponseEntity.ok().body(createGroupResponseDto);
        }

        for(Friend friend : friends){
            //친구의 그룹 골을 확인
            Optional<Member> friendById = memberRepository.findById(friend.getFriend().getId());
            Optional<GroupGoal> friendGroupGoal = Optional.ofNullable(friendById.get().getGroupGoal());

            if(friendGroupGoal.isPresent()){
                //이미 진행중인 챌린지 있음
                if(friendGroupGoal.get().isAcceptedGroup()){
                    if (friendById.isPresent()){
                        groupMembers.add(new CreateGroupMemberDto(friendById.get().getNickname(),false));
                    }
                }
                //진행중인 챌린지 없고, 대기만 있음
                else{
                    if(friendById.isPresent()){
                        groupMembers.add(new CreateGroupMemberDto(friendById.get().getNickname(),true));
                    }
                }
            }
            else{//초대받은 챌린지 없고 진행중인것도 없을때
                if (friendById.isPresent()){
                    groupMembers.add(new CreateGroupMemberDto(friendById.get().getNickname(),true));
                }
            }
        }

        CreateGroupResponseDto groupResponseDto = new CreateGroupResponseDto(groupMembers);
        return ResponseEntity.ok().body(groupResponseDto);
    }

    @Override
    @Transactional
    public ResponseEntity<String> exitChallenge(Long id) {

        Optional<GroupGoal> groupGoal = groupGoalRepository.findById(id);
        if(groupGoal.isPresent() && !groupGoal.get().isAcceptedGroup()){

            List<Member> members = groupGoal.get().getMembers();
            while (members.size() > 0){
                groupGoal.get().removeMember(members.get(0));
            }
            groupGoalRepository.deleteGroupGoalById(id);
        }
        return ResponseEntity.ok().body("같이해부자 나가기 완료");
    }
}
