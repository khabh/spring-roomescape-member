package roomescape.controller.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import roomescape.auth.JwtTokenProvider;
import roomescape.dto.TokenRequest;
import roomescape.model.Member;
import roomescape.model.Role;
import roomescape.repository.MemberRepository;
import roomescape.service.AuthService;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {"/reset.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LoginControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void initPort() {
        RestAssured.port = port;
    }

    @DisplayName("로그인 시 쿠키에 토큰 정보를 담아서 반환")
    @Test
    void login() {
        final String email = "111@aaa.com";
        final String password = "abc1234";
        final Map<String, String> params = Map.of("email", email, "password", password);
        final Member member = memberRepository.save(new Member("감자", Role.USER, email, password));

        final ExtractableResponse<Response> response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(params)
                .when().post("/login")
                .then().log().all()
                .statusCode(200)
                .extract();

        final String tokenValue = extractTokenValue(response.header("Set-Cookie"));
        assertThat(jwtTokenProvider.getMemberId(tokenValue)).isEqualTo(member.getId());
    }

    private static String extractTokenValue(String setCookieHeaderValue) {
        return Optional.ofNullable(setCookieHeaderValue)
                .filter(headerValue -> headerValue.contains("token="))
                .map(headerValue -> headerValue.substring(headerValue.indexOf("token=") + 6, headerValue.indexOf(";", headerValue.indexOf("token="))))
                .filter(tokenValue -> !tokenValue.isEmpty())
                .orElseThrow();
    }

    @DisplayName("토큰 정보를 추출해서 멤버 반환")
    @Test
    void checkLogin() {
        final String email = "111@aaa.com";
        final String password = "abc1234";
        final Map<String, String> params = Map.of("email", email, "password", password);
        memberRepository.save(new Member("감자", Role.USER, email, password));
        final String token = authService.createToken(new TokenRequest(email, password)).accessToken();

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .cookie("token", token)
                .body(params)
                .when().get("/login/check")
                .then().log().all()
                .statusCode(200);
    }
}