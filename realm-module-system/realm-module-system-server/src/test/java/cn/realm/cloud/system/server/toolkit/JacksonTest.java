package cn.realm.cloud.system.server.toolkit;

import cn.realm.cloud.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JacksonTest {

    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapper();

    private static final String BACK_INFO_JSON = """
            {
              "BackInfo": {
                "PlateNo": "辽CD8828",
                "FileNo": "210321030861",
                "AllowNum": "2人",
                "TotalMass": "--",
                "CurbWeight": "9400kg",
                "LoadQuality": "--",
                "ExternalSize": "7375×2550×3560mm",
                "Marks": "强制报废期止:2033-11-20",
                "Record": "检验有效期至2019年11月辽C(台安)天然气",
                "TotalQuasiMass": "39400kg"
              },
              "RecognizeWarnCode": [-9105, -9104],
              "RecognizeWarnMsg": [
                "WARN_DRIVER_LICENSE_BLUR",
                "WARN_DRIVER_LICENSE_REFLECTION"
              ],
              "RequestId": "26dcae75-1dff-44c3-807f-b15cfd1ddfb6"
            }
            """;

    private static final String FRONT_INFO_JSON = """
            {
              "FrontInfo": {
                "PlateNo": "辽CD8828",
                "VehicleType": "重型半挂牵引车",
                "Owner": "台安金钵运输有限公司",
                "Address": "辽宁省鞍山市台安县富家镇本街",
                "UseCharacter": "货运",
                "Model": "陕汽牌SX4258GR384TL",
                "Vin": "LZGJLG848JX100098",
                "EngineNo": "3118K049964",
                "RegisterDate": "2018-11-20",
                "IssueDate": "2018-11-20",
                "Seal": "辽宁省鞍山市公安局交通警察支队"
              },
              "RecognizeWarnCode": [],
              "RecognizeWarnMsg": [],
              "RequestId": "6f27e321-9453-445d-bb31-21c303991bde"
            }
            """;

    @Test
    @DisplayName("Parse FrontInfo JSON and extract RequestId")
    void shouldExtractRequestId_whenParsingFrontInfo() throws Exception {
        JsonNode root = MAPPER.readTree(FRONT_INFO_JSON);

        assertTrue(root.hasNonNull("RequestId"), "RequestId should exist and be non-null");
        assertEquals(
                "6f27e321-9453-445d-bb31-21c303991bde",
                root.get("RequestId").asText(),
                "RequestId should match the expected value"
        );
    }

    @Test
    @DisplayName("Parse BackInfo JSON and extract PlateNo")
    void shouldExtractPlateNo_whenParsingBackInfo() throws Exception {
        JsonNode root = MAPPER.readTree(BACK_INFO_JSON);

        String plateNo = root.path("BackInfo")
                .path("PlateNo")
                .asText("未知车牌");

        assertEquals("辽CD8828", plateNo, "PlateNo should match the expected value");
    }

    @Test
    @DisplayName("Return default value when PlateNo is missing")
    void shouldReturnDefault_whenPlateNoMissing() throws Exception {
        JsonNode root = MAPPER.readTree("""
                {
                  "BackInfo": {}
                }
                """);

        String plateNo = root.path("BackInfo")
                .path("PlateNo")
                .asText("未知车牌");

        assertEquals("未知车牌", plateNo, "Should fall back to default PlateNo");
    }

    @Test
    @DisplayName("Parse BackInfo JSON and extract warn codes")
    void shouldExtractWarnCodes_whenParsingBackInfo() throws Exception {
        JsonNode root = MAPPER.readTree(BACK_INFO_JSON);

        JsonNode codes = root.get("RecognizeWarnCode");

        assertNotNull(codes, "RecognizeWarnCode should not be null");
        assertTrue(codes.isArray(), "RecognizeWarnCode should be an array");
        assertEquals(2, codes.size(), "RecognizeWarnCode size should be 2");
        assertEquals(-9105, codes.get(0).asInt(), "First warn code should be -9105");
        assertEquals(-9104, codes.get(1).asInt(), "Second warn code should be -9104");
    }
}
