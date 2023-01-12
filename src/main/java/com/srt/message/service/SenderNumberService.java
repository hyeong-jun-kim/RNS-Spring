package com.srt.message.service;

import com.srt.message.config.exception.BaseException;
import com.srt.message.config.page.PageResult;
import com.srt.message.config.response.BaseResponse;
import com.srt.message.config.status.AuthPhoneNumberStatus;
import com.srt.message.config.status.BaseStatus;
import com.srt.message.domain.SenderNumber;
import com.srt.message.domain.redis.AuthPhoneNumber;
import com.srt.message.dto.sender_number.post.RegisterSenderNumberReq;
import com.srt.message.dto.sender_number.post.RegisterSenderNumberRes;
import com.srt.message.repository.SenderNumberRepository;
import com.srt.message.repository.redis.AuthPhoneNumberRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

import static com.srt.message.config.response.BaseResponseStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SenderNumberService {
    private final SenderNumberRepository senderNumberRepository;

    private final AuthPhoneNumberRedisRepository authPhoneNumberRedisRepository;

    // audit 저장 확인 용 테스트 저장
    // 나중에 무조건 지워야 함
    @Transactional(readOnly = false)
    public BaseResponse<String> testSave(){
        SenderNumber senderNumber = SenderNumber.builder()
                .phoneNumber("01012341234")
                .build();

        senderNumberRepository.save(senderNumber);

        return new BaseResponse<>("OK");
    }

    // 발신자 등록
    @Transactional(readOnly = false)
    public RegisterSenderNumberRes registerSenderNumber(RegisterSenderNumberReq registerSenderNumberReq){
        String phoneNumber = registerSenderNumberReq.getPhoneNumber();

        if(senderNumberRepository.findByPhoneNumberAndStatus(phoneNumber, BaseStatus.ACTIVE).isPresent())
            throw new BaseException(ALREADY_EXIST_PHONE_NUMBER);

        // 휴대폰 인증 확인
        AuthPhoneNumber authPhoneNumber = authPhoneNumberRedisRepository.findById(phoneNumber)
                .orElseThrow(() -> new BaseException(NOT_AUTH_PHONE_NUMBER));

        if(authPhoneNumber.getAuthPhoneNumberStatus() != AuthPhoneNumberStatus.CONFIRM)
            throw new BaseException(NOT_AUTH_PHONE_NUMBER);

        SenderNumber senderNumber = RegisterSenderNumberReq.toEntity(registerSenderNumberReq);

        senderNumberRepository.save(senderNumber);

        return RegisterSenderNumberRes.toDto(senderNumber);
    }

    // 발신자 조회
    public PageResult<RegisterSenderNumberRes, SenderNumber> getMemberSenderNumber(long memberId, int page) {
        PageRequest pageRequest = PageRequest.of(page, 3, Sort.by("id").descending());
        Page<SenderNumber> senderNumberPage = senderNumberRepository.findByMemberIdAndStatus(memberId, BaseStatus.ACTIVE, pageRequest)
                .orElseThrow(() -> new BaseException(NOT_EXIST_PHONE_NUMBER));
        Function<SenderNumber, RegisterSenderNumberRes> fn = (senderNumber -> RegisterSenderNumberRes.toDto(senderNumber));
        return new PageResult<>(senderNumberPage, fn);
//        return senderNumberPage.map(senderNumber -> RegisterSenderNumberRes.toDto(senderNumber));
    }

    // 발신자 삭제
    @Transactional(readOnly = false)
    public void deleteSenderNumber(long senderNumberId, long memberId) {
        SenderNumber senderNumber = senderNumberRepository.findByIdAndStatus(senderNumberId, BaseStatus.ACTIVE)
                .orElseThrow(() -> new BaseException(NOT_EXIST_PHONE_NUMBER));

        if (senderNumber.getMember().getId() == memberId) {
            senderNumber.changeStatusInActive();
            senderNumberRepository.save(senderNumber);
        } else {
            throw new BaseException(NOT_AUTH_MEMBER);
        }

    }
}
