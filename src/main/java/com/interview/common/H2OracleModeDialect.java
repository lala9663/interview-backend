package com.interview.common;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.SQL2008StandardLimitHandler;

/**
 * local·test 용 방언. H2 를 Oracle 호환 모드(MODE=Oracle)로 쓰면 H2Dialect 가 만드는 "limit ?" 문법이 거부된다.
 * 그래서 페이지 문법만 표준(offset ? rows fetch next ? rows only)으로 바꾼다. Oracle 12c 이상과 같은 문법이다.
 * application-local.yml 의 spring.jpa.database-platform 에서 지정한다. dev/prod 는 Oracle12cDialect 를 쓴다.
 */
public class H2OracleModeDialect extends H2Dialect {

    @Override
    public LimitHandler getLimitHandler() {
        return SQL2008StandardLimitHandler.INSTANCE;
    }
}
