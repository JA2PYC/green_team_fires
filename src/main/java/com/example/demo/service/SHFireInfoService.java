package com.example.demo.service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.entity.FireInfoSidoDamageEntity;
import com.example.demo.entity.FireInfoSidoCasualtyEntity;
import com.example.demo.entity.FireInfoSidoEntity;
import com.example.demo.repository.FireInfoSidoCasualtyRepository;
import com.example.demo.repository.FireInfoSidoDamageRepository;
import com.example.demo.repository.FireInfoSidoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

@Service
public class SHFireInfoService {
    private final String FIRE_INFO_API_KEY;
    private static final String FIRE_INFO_URL = "http://apis.data.go.kr/1661000/FireInformationService";

    private final FireInfoSidoRepository fireInfoSidoRepository;
    private final FireInfoSidoCasualtyRepository fireInfoSidoCasualtyRepository;
    private final FireInfoSidoDamageRepository fireInfoSidoDamageRepository;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Constructor
    public SHFireInfoService(FireInfoSidoRepository fireInfoSidoRepository,
            FireInfoSidoCasualtyRepository fireInfoSidoCasualtyRepository,
            FireInfoSidoDamageRepository fireInfoSidoDamageRepository) {
        if (Files.exists(Paths.get(".env"))) {
            Dotenv dotenv;

            try {
                dotenv = Dotenv.configure().load();
            } catch (Exception e) {
                System.err.println("Can't load ENV.");
                dotenv = null;
            }

            this.FIRE_INFO_API_KEY = dotenv != null ? dotenv.get("FIRE_INFO_API_KEY") : null;

            if (this.FIRE_INFO_API_KEY == null || this.FIRE_INFO_API_KEY.isEmpty()) {
                System.err.println("FIRE_INFO_API_KEY is missing!");
            }
        } else {
            System.err.println("ENV file not found.");
            this.FIRE_INFO_API_KEY = null;
        }

        this.fireInfoSidoRepository = fireInfoSidoRepository;
        this.fireInfoSidoCasualtyRepository = fireInfoSidoCasualtyRepository;
        this.fireInfoSidoDamageRepository = fireInfoSidoDamageRepository;
    }

    // Get data from DB
    public List<FireInfoSidoEntity> getFireInfoSido() {
        // System.out.println("FireInfoService - getFireInfo");
        LocalDate today = LocalDate.now();
        List<FireInfoSidoEntity> response = fireInfoSidoRepository.findByOcrnYmd(today);

        return response;
    }

    // Update data every hour
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void updateFireInfoSidoData() {
        // System.out.println("FireInfoService - updateFireInfoSidoData // Update Scheduled!");
        String response = fetchFireInfoSidoData();
        // System.out.println("FireInfoService - updateFireInfoSidoData - response : " +
        // response);

        try {
            List<FireInfoSidoEntity> fireInfoList = new ArrayList<>();
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response);
            // JsonNode itemsNode = jsonNode.get("response").get("body").get("items");
            JsonNode itemsNode = jsonNode.path("response").path("body").path("items").path("item");

            // System.out.println("FireInfoService - updateFireInfoSidoData - itemsNode : "
            // + itemsNode);
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    // System.out.println("FireInfoService - updateFireInfoSidoData - item : " +
                    // item);
                    FireInfoSidoEntity entity = new FireInfoSidoEntity();
                    entity.setSido_nm(item.get("sidoNm").asText());
                    entity.setFire_rcpt_mnb(item.get("fireRcptMnb").asInt());
                    entity.setStn_end_mnb(item.get("stnEndMnb").asInt());
                    entity.setSlf_extsh_mnb(item.get("slfExtshMnb").asInt());
                    entity.setFlsrp_prcs_mnb(item.get("flsrpPrcsMnb").asInt());
                    entity.setFals_dclr_mnb(item.get("falsDclrMnb").asInt());
                    entity.setOcrn_ymd(LocalDate.parse(item.get("ocrnYmd").asText(), formatter));

                    fireInfoList.add(entity);
                }

                saveFireInfoSidoData(fireInfoList);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] FireInfoService - updateFireInfoSidoData / Fail to parse JSON!");
        }
    }

    // Fetch data from API
    public String fetchFireInfoSidoData() {
        if (FIRE_INFO_API_KEY == null || FIRE_INFO_API_KEY.isEmpty()) {
            return "FireInfoService - API Key is missing! Check ENF!";
        }

        LocalDate localDate = LocalDate.now();
        String today = localDate.toString().replace("-", "");

        String url = UriComponentsBuilder.fromUriString(FIRE_INFO_URL + "/getOcBysidoFireSmrzPcnd")
                .queryParam("serviceKey", FIRE_INFO_API_KEY)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 100)
                .queryParam("resultType", "json")
                .queryParam("ocrn_ymd", today)
                .build().toUriString();

        // System.out.println("FireInfo - url : " + url);

        WebClient webClient = WebClient.create();
        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response;
        } catch (Exception e) {
            System.err.println("[ERROR] FireInfoSerbice - Webclient / Fail to fetch fire information data!");
            return "Failed to fetch fire information data!";
        }
    }

    // Save data to DB
    private void saveFireInfoSidoData(List<FireInfoSidoEntity> fireInfoList) {
        List<FireInfoSidoEntity> entitiesToSave = new ArrayList<>();

        for (FireInfoSidoEntity entity : fireInfoList) {
            FireInfoSidoEntity fireInfoSidoEntity = fireInfoSidoRepository
                    .findBySidoNmAndOcrnYmd(entity.getSido_nm(), entity.getOcrn_ymd())
                    .orElse(new FireInfoSidoEntity());

            fireInfoSidoEntity.setSido_nm(entity.getSido_nm());
            fireInfoSidoEntity.setFire_rcpt_mnb(entity.getFire_rcpt_mnb());
            fireInfoSidoEntity.setStn_end_mnb(entity.getStn_end_mnb());
            fireInfoSidoEntity.setSlf_extsh_mnb(entity.getSlf_extsh_mnb());
            fireInfoSidoEntity.setFlsrp_prcs_mnb(entity.getFlsrp_prcs_mnb());
            fireInfoSidoEntity.setFals_dclr_mnb(entity.getFals_dclr_mnb());
            fireInfoSidoEntity.setOcrn_ymd(entity.getOcrn_ymd());

            entitiesToSave.add(fireInfoSidoEntity);
        }
        fireInfoSidoRepository.saveAll(entitiesToSave);
        // System.out.println("FireInfoService - saveFireInfoSidoData - Data saved!");
    }

    // Get Sido Damage data from DB
    public List<FireInfoSidoCasualtyEntity> getFireInfoSidoCasualty() {
        // System.out.println("FireInfoService - getFireInfoSidoCasualty");
        LocalDate targetDate = LocalDate.now().minusDays(7);
        List<FireInfoSidoCasualtyEntity> response = fireInfoSidoCasualtyRepository.findByOcrnYmdAfter(targetDate);

        return response;
    }

    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void updateFireInfoSidoCasualtyData() {
        // System.out.println("FireInfoService - updateFireInfoSidoCasualtyData // Update Scheduled!");
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 15; i++) {
            LocalDate targetDate = today.minusDays(i);
            // System.out.println("targetDate" + targetDate);
            String response = fetchFireInfoSidoCasualtyData(targetDate);
            // System.out.println("FireInfoService - updateFireInfoSidoCasualtyData - response : " + response);
            try {
                List<FireInfoSidoCasualtyEntity> fireInfoList = new ArrayList<>();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode itemsNode = jsonNode.path("response").path("body").path("items").path("item");

                if (itemsNode.isArray()) {
                    for (JsonNode item : itemsNode) {
                        FireInfoSidoCasualtyEntity entity = new FireInfoSidoCasualtyEntity();
                        entity.setSidoNm(item.get("sidoNm").asText());
                        entity.setVctmPercnt(item.get("vctmPercnt").asInt());
                        entity.setInjrdprPercnt(item.get("injrdprPercnt").asInt());
                        entity.setLifeDmgPercnt(item.get("lifeDmgPercnt").asInt());
                        entity.setOcrnMnb(item.get("ocrnMnb").asInt());
                        entity.setOcrnYmd(LocalDate.parse(item.get("ocrnYmd").asText(), formatter));

                        fireInfoList.add(entity);
                    }

                    saveFireInfoSidoCasualtyData(fireInfoList);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] FireInfoService - updateFireInfoSidoCasualtyData / Fail to parse JSON!");
            }
        }
    }

    // Fetch Sido Casualty data from API
    public String fetchFireInfoSidoCasualtyData(LocalDate targetDate) {
        if (FIRE_INFO_API_KEY == null || FIRE_INFO_API_KEY.isEmpty()) {
            return "FireInfoService - API Key is missing! Check ENF!";
        }

        // LocalDate localDate = LocalDate.now().minusDays(1);
        String today = targetDate.toString().replace("-", "");

        String url = UriComponentsBuilder.fromUriString(FIRE_INFO_URL + "/getOcBysidoFpcnd")
                .queryParam("servicekey", FIRE_INFO_API_KEY)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 100)
                .queryParam("resultType", "json")
                .queryParam("ocrn_ymd", today)
                .build().toUriString();

        WebClient webClient = WebClient.create();
        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response;
        } catch (Exception e) {
            System.err.println("[ERROR] FireInfoService - Webclient / Fail to fetch fire information data!");
            e.printStackTrace();
            return "Failed to fetch fire information data!";
        }
    }

    private void saveFireInfoSidoCasualtyData(List<FireInfoSidoCasualtyEntity> fireInfoCasualtyList) {
        List<FireInfoSidoCasualtyEntity> entitiesToSave = new ArrayList<>();

        for (FireInfoSidoCasualtyEntity entity : fireInfoCasualtyList) {
            FireInfoSidoCasualtyEntity fireInfoSidoCasualtyEntity = fireInfoSidoCasualtyRepository
                    .findBySidoNmAndOcrnYmd(entity.getSidoNm(), entity.getOcrnYmd())
                    .orElse(new FireInfoSidoCasualtyEntity());

            fireInfoSidoCasualtyEntity.setSidoNm(entity.getSidoNm());
            fireInfoSidoCasualtyEntity.setVctmPercnt(entity.getVctmPercnt());
            fireInfoSidoCasualtyEntity.setInjrdprPercnt(entity.getInjrdprPercnt());
            fireInfoSidoCasualtyEntity.setLifeDmgPercnt(entity.getLifeDmgPercnt());
            fireInfoSidoCasualtyEntity.setOcrnMnb(entity.getOcrnMnb());
            fireInfoSidoCasualtyEntity.setOcrnYmd(entity.getOcrnYmd());

            entitiesToSave.add(fireInfoSidoCasualtyEntity);
        }
        fireInfoSidoCasualtyRepository.saveAll(entitiesToSave);
        // System.out.println("FireInfoService - saveFireInfoSidoCasualtyData - Data saved!");
    }

    // Get Sido Casualty data from DB
    public List<FireInfoSidoDamageEntity> getFireInfoSidoDamage() {
        // System.out.println("FireInfoService - getFireInfoSidoDamage");
        LocalDate targetDate = LocalDate.now().minusDays(7);
        List<FireInfoSidoDamageEntity> response = fireInfoSidoDamageRepository.findByOcrnYmdAfter(targetDate);

        return response;
    }

    // Update Damage Data every hour
    @Scheduled(fixedRate = 1000 * 60 * 60)
    public void updateFireInfoSidoDamageData() {
        // System.out.println("FireInfoService - updateFireInfoSidoDamageData // Update Scheduled!");
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 15; i++ ) {
            LocalDate targetDate = today.minusDays(i);
            String response = fetchFireInfoSidoDamageData(targetDate);

            try {
                List<FireInfoSidoDamageEntity> entityList = new ArrayList<> ();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode itemsNode = jsonNode.path("response").path("body").path("items").path("item");

                if (itemsNode.isArray()) {
                    for (JsonNode item : itemsNode) {
                        FireInfoSidoDamageEntity entity = new FireInfoSidoDamageEntity();
                        entity.setSidoNm(item.get("sidoNm").asText());
                        entity.setPrptDmgSbttAmt(item.get("prptDmgSbttAmt").asInt());
                        entity.setOcrnMnb(item.get("ocrnMnb").asInt());
                        entity.setOcrnYmd(LocalDate.parse(item.get("ocrnYmd").asText(), formatter));

                        entityList.add(entity);
                    }
                }

                saveFireInfoSidoDamageData(entityList);

            } catch (Exception e) {
                System.err.println("[ERROR] FireInfoService - updateFireInfoSidoDamageData / Fail to parse JSON!");
                e.printStackTrace();
            }
        }
    }

    // Fetch Sido Damage data from API
    public String fetchFireInfoSidoDamageData(LocalDate targetDate) {
        if (FIRE_INFO_API_KEY == null || FIRE_INFO_API_KEY.isEmpty()) {
            return "FireInfoService - API Key is missing! Check ENF!";
        }

        // LocalDate localDate = LocalDate.now().minusDays(1);
        String today = targetDate.toString().replace("-", "");

        String url = UriComponentsBuilder.fromUriString(FIRE_INFO_URL + "/getOcBysidoFireOcrnPcnd")
                .queryParam("servicekey", FIRE_INFO_API_KEY)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 100)
                .queryParam("resultType", "json")
                .queryParam("ocrn_ymd", today)
                .build().toUriString();

        WebClient webClient = WebClient.create();
        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return response;
        } catch (Exception e) {
            System.err.println("[ERROR] FireInfoService - Webclient / Fail to fetch fire information data!");
            return "Failed to fetch fire information data!";
        }
    }

    private void saveFireInfoSidoDamageData(List<FireInfoSidoDamageEntity> entityList) {
        List<FireInfoSidoDamageEntity> entitiesToSave = new ArrayList<>();

        for (FireInfoSidoDamageEntity entity : entityList) {
            FireInfoSidoDamageEntity entityToSave = fireInfoSidoDamageRepository
                    .findBySidoNmAndOcrnYmd(entity.getSidoNm(), entity.getOcrnYmd())
                    .orElse(new FireInfoSidoDamageEntity());

            entityToSave.setSidoNm(entity.getSidoNm());
            entityToSave.setPrptDmgSbttAmt(entity.getPrptDmgSbttAmt());
            entityToSave.setOcrnMnb(entity.getOcrnMnb());
            entityToSave.setOcrnYmd(entity.getOcrnYmd());

            entitiesToSave.add(entityToSave);
        }

        fireInfoSidoDamageRepository.saveAll(entitiesToSave);
        // System.out.println("FireInfoService - saveFireInfoSidoDamageData - Data saved!");
    }

}
