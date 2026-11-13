-- 면접 지원 백엔드 Oracle 스키마.
-- 이 파일은 자동 생성된다. 직접 고치지 말고 엔티티를 고친 뒤 ./gradlew generateOracleSchema 를 실행한다.
-- 운영 DB 에 이미 테이블이 있으면 이 파일을 통째로 실행하지 말고, 바뀐 부분만 ALTER 문으로 옮긴다(docs/database.md).

create sequence iv_ai_evaluation_seq start with 1 increment by  1;
create sequence iv_ai_job_seq start with 1 increment by  1;
create sequence iv_briefing_seq start with 1 increment by  1;
create sequence iv_candidate_seq start with 1 increment by  1;
create sequence iv_eval_criterion_seq start with 1 increment by  1;
create sequence iv_evaluation_seq start with 1 increment by  1;
create sequence iv_follow_up_seq start with 1 increment by  1;
create sequence iv_interview_assignee_seq start with 1 increment by  1;
create sequence iv_interview_seq start with 1 increment by  1;
create sequence iv_job_posting_seq start with 1 increment by  1;
create sequence iv_question_bank_seq start with 1 increment by  1;
create sequence iv_question_seq start with 1 increment by  1;
create sequence iv_recruitment_seq start with 1 increment by  1;
create sequence iv_report_seq start with 1 increment by  1;
create sequence iv_room_event_seq start with 1 increment by  1;
create sequence iv_room_seq start with 1 increment by  1;
create sequence iv_upload_batch_seq start with 1 increment by  1;
create sequence iv_upload_error_seq start with 1 increment by  1;
create sequence iv_user_account_seq start with 1 increment by  1;
create sequence iv_utterance_seq start with 1 increment by  1;

    create table iv_ai_evaluation (
       id number(19,0) not null,
        generated_at timestamp,
        interview_id number(19,0) not null,
        model_name varchar2(100 char),
        overall_comment clob,
        scores_json clob,
        primary key (id)
    );

    create table iv_ai_job (
       id number(19,0) not null,
        created_at timestamp not null,
        error_code varchar2(50 char),
        error_message varchar2(1000 char),
        finished_at timestamp,
        interview_id number(19,0) not null,
        payload varchar2(1000 char),
        started_at timestamp,
        status varchar2(20 char) not null,
        job_type varchar2(20 char) not null,
        worker_id varchar2(100 char),
        primary key (id)
    );

    create table iv_briefing (
       id number(19,0) not null,
        excluded_fields varchar2(500 char),
        generated_at timestamp,
        interview_id number(19,0) not null,
        model_name varchar2(100 char),
        status varchar2(20 char) not null,
        summary_json clob,
        primary key (id)
    );

    create table iv_candidate (
       id number(19,0) not null,
        applicant_no varchar2(50 char) not null,
        blinded_fields varchar2(500 char),
        created_at timestamp not null,
        job_code varchar2(30 char) not null,
        job_title varchar2(200 char),
        name varchar2(100 char) not null,
        profile_json clob,
        recruitment_id number(19,0) not null,
        source_kind varchar2(20 char),
        track varchar2(20 char) not null,
        updated_at timestamp,
        primary key (id)
    );

    create table iv_eval_criterion (
       id number(19,0) not null,
        code varchar2(50 char) not null,
        description varchar2(1000 char),
        interview_type varchar2(20 char) not null,
        max_score number(10,0) not null,
        name varchar2(100 char) not null,
        order_no number(10,0) not null,
        track varchar2(20 char) not null,
        primary key (id)
    );

    create table iv_evaluation (
       id number(19,0) not null,
        grade varchar2(20 char),
        interview_id number(19,0) not null,
        memo clob,
        scores_json clob,
        is_submitted number(1,0) not null,
        submitted_at timestamp,
        updated_at timestamp,
        user_id number(19,0) not null,
        primary key (id)
    );

    create table iv_follow_up (
       id number(19,0) not null,
        based_on_seq number(10,0),
        client_id varchar2(100 char),
        created_at timestamp not null,
        question_id number(19,0),
        rationale varchar2(1000 char),
        room_id number(19,0) not null,
        follow_up_text varchar2(1000 char) not null,
        is_used number(1,0) not null,
        primary key (id)
    );

    create table iv_interview (
       id number(19,0) not null,
        candidate_id number(19,0) not null,
        created_at timestamp not null,
        duration_minutes number(10,0),
        interview_mode varchar2(20 char) not null,
        recruitment_id number(19,0) not null,
        scheduled_at timestamp,
        seq_no number(10,0) not null,
        status varchar2(20 char) not null,
        track varchar2(20 char) not null,
        interview_type varchar2(20 char) not null,
        primary key (id)
    );

    create table iv_interview_assignee (
       id number(19,0) not null,
        interview_id number(19,0) not null,
        matched_by varchar2(20 char) not null,
        user_id number(19,0) not null,
        primary key (id)
    );

    create table iv_job_posting (
       id number(19,0) not null,
        jd_text clob,
        job_code varchar2(30 char) not null,
        recruitment_id number(19,0) not null,
        title varchar2(200 char) not null,
        updated_at timestamp,
        primary key (id)
    );

    create table iv_question (
       id number(19,0) not null,
        is_asked number(1,0) not null,
        asked_at_seq number(10,0),
        category varchar2(100 char),
        created_at timestamp not null,
        is_hidden number(1,0) not null,
        intent varchar2(1000 char),
        interview_id number(19,0) not null,
        order_no number(10,0) not null,
        parent_question_id number(19,0),
        source_kind varchar2(20 char) not null,
        question_text varchar2(1000 char) not null,
        primary key (id)
    );

    create table iv_question_bank (
       id number(19,0) not null,
        category varchar2(100 char),
        created_at timestamp not null,
        interview_type varchar2(20 char),
        job_code varchar2(30 char),
        sample_answer clob,
        question_text varchar2(1000 char) not null,
        primary key (id)
    );

    create table iv_recruitment (
       id number(19,0) not null,
        created_at timestamp not null,
        end_dt date,
        start_dt date,
        title varchar2(200 char) not null,
        track varchar2(20 char) not null,
        primary key (id)
    );

    create table iv_report (
       id number(19,0) not null,
        basis varchar2(20 char) not null,
        content_json clob,
        detail_level varchar2(20 char) not null,
        generated_at timestamp not null,
        interview_id number(19,0) not null,
        model_name varchar2(100 char),
        version_no number(10,0) not null,
        primary key (id)
    );

    create table iv_room (
       id number(19,0) not null,
        created_at timestamp not null,
        ended_at timestamp,
        interview_id number(19,0) not null,
        post_stt_model varchar2(100 char),
        started_at timestamp,
        status varchar2(20 char) not null,
        primary key (id)
    );

    create table iv_room_event (
       id number(19,0) not null,
        created_at timestamp not null,
        data_json clob,
        message varchar2(1000 char),
        room_id number(19,0) not null,
        event_type varchar2(50 char) not null,
        primary key (id)
    );

    create table iv_upload_batch (
       id number(19,0) not null,
        failed_rows number(10,0) not null,
        file_name varchar2(300 char),
        recruitment_id number(19,0) not null,
        success_rows number(10,0) not null,
        total_rows number(10,0) not null,
        uploaded_at timestamp not null,
        uploaded_by_id number(19,0),
        uploaded_by_name varchar2(100 char),
        primary key (id)
    );

    create table iv_upload_error (
       id number(19,0) not null,
        batch_id number(19,0) not null,
        cell_value varchar2(500 char),
        error_code varchar2(50 char),
        column_name varchar2(100 char),
        error_message varchar2(500 char),
        row_no number(10,0) not null,
        primary key (id)
    );

    create table iv_user_account (
       id number(19,0) not null,
        is_active number(1,0) not null,
        department varchar2(100 char),
        employee_no varchar2(30 char) not null,
        job_codes varchar2(500 char),
        name varchar2(100 char) not null,
        role_code varchar2(20 char) not null,
        primary key (id)
    );

    create table iv_utterance (
       id number(19,0) not null,
        end_ms number(19,0),
        matched_question_id number(19,0),
        raw_text clob,
        refined_text clob,
        room_id number(19,0) not null,
        seq_no number(10,0) not null,
        source_kind varchar2(20 char) not null,
        speaker varchar2(20 char) not null,
        speaker_method varchar2(20 char) not null,
        start_ms number(19,0),
        primary key (id)
    );

    alter table iv_ai_evaluation 
       add constraint UK_IV_AI_EVAL_INTERVIEW unique (interview_id);
create index IX_IV_AI_JOB_INTERVIEW on iv_ai_job (interview_id);
create index IX_IV_AI_JOB_STATUS on iv_ai_job (status);

    alter table iv_briefing 
       add constraint UK_IV_BRIEFING_INTERVIEW unique (interview_id);

    alter table iv_candidate 
       add constraint UK_IV_CANDIDATE_APPL_NO unique (recruitment_id, applicant_no);

    alter table iv_eval_criterion 
       add constraint UK_IV_EVAL_CRITERION unique (track, interview_type, code);

    alter table iv_evaluation 
       add constraint UK_IV_EVALUATION unique (interview_id, user_id);
create index IX_IV_FOLLOW_UP_ROOM on iv_follow_up (room_id);
create index IX_IV_INTERVIEW_CAND on iv_interview (candidate_id);
create index IX_IV_INTERVIEW_RECR on iv_interview (recruitment_id);
create index IX_IV_INTERVIEW_SCHED on iv_interview (scheduled_at);
create index IX_IV_ASSIGNEE_USER on iv_interview_assignee (user_id);

    alter table iv_interview_assignee 
       add constraint UK_IV_ASSIGNEE unique (interview_id, user_id);

    alter table iv_job_posting 
       add constraint UK_IV_JOB_POSTING_CODE unique (recruitment_id, job_code);
create index IX_IV_QUESTION_INTERVIEW on iv_question (interview_id);
create index IX_IV_QBANK_JOB on iv_question_bank (job_code);

    alter table iv_report 
       add constraint UK_IV_REPORT_VERSION unique (interview_id, version_no);

    alter table iv_room 
       add constraint UK_IV_ROOM_INTERVIEW unique (interview_id);
create index IX_IV_ROOM_EVENT_ROOM on iv_room_event (room_id);
create index IX_IV_UPLOAD_BATCH_RECR on iv_upload_batch (recruitment_id);
create index IX_IV_UPLOAD_ERROR_BATCH on iv_upload_error (batch_id);

    alter table iv_user_account 
       add constraint UK_IV_USER_EMP_NO unique (employee_no);

    alter table iv_utterance 
       add constraint UK_IV_UTTERANCE_SEQ unique (room_id, seq_no);
