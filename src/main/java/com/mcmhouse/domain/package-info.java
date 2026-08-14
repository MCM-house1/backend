/**
 * 도메인 모델 패키지.
 *
 * <p>타입 선택 규칙(일관성 유지용):
 * <ul>
 *   <li><b>@Entity 클래스</b> — DB에 저장되는 가변 상태. (예: {@link com.mcmhouse.domain.DiagnosisResult},
 *       {@link com.mcmhouse.domain.ZoneVisit}) JPA 요건상 record가 아니라 클래스로 둔다.</li>
 *   <li><b>record</b> — 저장하지 않는 불변 값 객체. (예: {@link com.mcmhouse.domain.Product})</li>
 *   <li><b>enum</b> — 고정된 값 집합. (예: {@link com.mcmhouse.domain.House})</li>
 * </ul>
 *
 * <p>데이터를 읽어들이는 로더(@Component)는 도메인 모델이 아니므로
 * {@code com.mcmhouse.catalog} 패키지에 둔다.
 * 요청/응답 DTO는 {@code com.mcmhouse.dto} 패키지에 둔다.
 */
package com.mcmhouse.domain;
