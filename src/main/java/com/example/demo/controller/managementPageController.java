package com.example.demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.dto.asReceptionDTO;
import com.example.demo.entity.dy_boardData;
import com.example.demo.service.asReceptionService;
import com.example.demo.service.dy_boardService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/managementPage")
public class managementPageController {
    @Autowired
    private dy_boardService service;

    @Autowired
    asReceptionService asReceptionService;
    
    @GetMapping("")
    public String getindex() {
        return "managementPage";
    }

    @GetMapping("/boardIndex")
    public String getBoardIndex() {
		return "BoardIndex";
	}

    // 공지사항 게시판 페이지
	@GetMapping("/noticeBoard")
    public String getNoticeBoard(Model model, @RequestParam(defaultValue = "1") int page) {
        int pageSize = 10; // 페이지당 게시물 수
        int offset = (page - 1) * pageSize;

        List<dy_boardData> boardList = service.getBoardList(pageSize, offset);
        model.addAttribute("boardlist", boardList);

        int totalCount = service.getTotalCount();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
		return "noticeBoard"; // jsp 파일 경로
	}

    // QnA 게시판 페이지
	@GetMapping("/qnaBoard")
    public String getQnaBoard(Model model, @RequestParam(defaultValue = "1") int page) {
        int pageSize = 10; // 페이지당 게시물 수
        int offset = (page - 1) * pageSize;

        List<dy_boardData> boardList = service.getBoardList(pageSize, offset);
        model.addAttribute("boardlist", boardList);

        int totalCount = service.getTotalCount();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
		return "qnaBoard"; // jsp 파일 경로
	}

    @GetMapping("/fullCalendar")
    public String loadFullCalendarPage(Model model) {
        return "fullCalendar"; // templates/fullCalendar.html
    }

    // FullCalendar 이벤트 데이터 제공 (JSON)
    @ResponseBody
    @GetMapping("/full-calendar-events")
    public List<Map<String, Object>> getFullCalendarEvents() {
        List<Map<String, Object>> events = new ArrayList<>();

        // 예제 데이터
        Map<String, Object> event1 = new HashMap<>();
        event1.put("title", "회의");
        event1.put("start", "2025-01-10");

        Map<String, Object> event2 = new HashMap<>();
        event2.put("title", "프로젝트 마감");
        event2.put("start", "2025-01-15");

        events.add(event1);
        events.add(event2);

        return events; // JSON으로 반환
    }

    // as 등록 페이지
    @GetMapping("/registAS")
    public String scheduleRegistAS(HttpSession session, Model model) {
        if (session.getAttribute("userId") == null) {
            model.addAttribute("msg", "로그인이 필요합니다.");
            model.addAttribute("url", "/login");
            return "/alertPrint";
        }
        return "registAS";
    }

    @PostMapping("/registAS/insert")
    public String insertAsReception(asReceptionDTO asReceptionDTO, Model model) {
        try {
            int result = asReceptionService.AS_Reception(asReceptionDTO);

            if (result > 0) {
                model.addAttribute("msg", "A/S 접수가 성공적으로 완료되었습니다.");
                model.addAttribute("url", "/managementPage"); // 목록 페이지 URL
            } else {
                model.addAttribute("msg", "A/S 접수 처리 중 문제가 발생했습니다.");
                model.addAttribute("url", "/managementPage/registAS"); // 접수 페이지 URL
            }

        } catch (Exception e) {
            model.addAttribute("msg", "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("url", "/managementPage/registAS");
        }

        return "/alertPrint";
    }

    // as 처리현황 페이지
	@GetMapping("/ASprocessStatus")
    public String getASprocessStatus() {
	// public String getProcessStatus(Model model) {
		// List<ReservationDTO> statusList = scheduleMapper.getStatusList();
		// model.addAttribute("statusList", statusList);
		return "ASprocessStatusBoard"; // jsp 파일 경로
	}

}
