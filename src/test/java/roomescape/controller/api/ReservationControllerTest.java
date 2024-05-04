package roomescape.controller.api;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.jdbc.Sql;
import roomescape.model.Reservation;
import roomescape.model.ReservationTime;
import roomescape.model.Theme;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ReservationTimeRepository;
import roomescape.repository.ThemeRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = {"/reset.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationTimeRepository reservationTimeRepository;

    @Autowired
    private ThemeRepository themeRepository;

    @BeforeEach
    void initPort() {
        RestAssured.port = port;
    }

    @DisplayName("예약 목록 조회")
    @Test
    void getReservations() {
        final ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("09:00")));
        final Theme theme = themeRepository.save(new Theme("이름1", "설명1", "썸네일1"));
        reservationRepository.save(new Reservation("이름1", LocalDate.now().plusMonths(2), reservationTime, theme));
        reservationRepository.save(new Reservation("이름2", LocalDate.now().plusMonths(1), reservationTime, theme));

        RestAssured.given().log().all()
                .when().get("/reservations")
                .then().log().all()
                .statusCode(200)
                .body("size()", is(2));
    }

    @DisplayName("예약 추가 및 삭제")
    @TestFactory
    Stream<DynamicTest> saveAndDeleteReservation() {
        final ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("09:00")));
        final Theme theme = themeRepository.save(new Theme("이름1", "설명1", "썸네일1"));

        return Stream.of(
                dynamicTest("예약을 추가한다", () -> {
                    final Map<String, Object> params = Map.of(
                            "name", "브라운",
                            "date", LocalDate.now().plusMonths(1),
                            "timeId", reservationTime.getId(),
                            "themeId", theme.getId());

                    RestAssured.given().log().all()
                            .contentType(ContentType.JSON)
                            .body(params)
                            .when().post("/reservations")
                            .then().log().all()
                            .statusCode(201)
                            .header("Location", "/reservations/1");
                }),
                dynamicTest("예약을 삭제한다", () ->
                        RestAssured.given().log().all()
                                .when().delete("/reservations/1")
                                .then().log().all()
                                .statusCode(204)
                )
        );
    }

    @DisplayName("유효하지 않은 날짜 형식 입력 시 BadRequest 반환")
    @ParameterizedTest
    @ValueSource(strings = {"2099.22.11", "2022", "abc"})
    void invalidDateFormat(final String date) {
        final ReservationTime reservationTime = reservationTimeRepository.save(new ReservationTime(LocalTime.parse("09:00")));
        final Theme theme = themeRepository.save(new Theme("이름1", "설명1", "썸네일1"));
        final Map<String, Object> params = Map.of(
                "name", "브라운",
                "date", date,
                "timeId", reservationTime.getId(),
                "themeId", theme.getId());

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(params)
                .when().post("/reservations")
                .then().log().all()
                .statusCode(400)
                .body(equalTo("잘못된 입력 형식입니다."));
    }
}
