package com.hk.chart.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class UserController {
	
	@GetMapping("/login")
    public String loginForm() {
        return "login";
    }
	
	@GetMapping("/signUp")
    public String signUpForm() {
        return "signUp";
    }

	@GetMapping("/dashBoard")
    public String dashBoard() {
        return "MainDashBoard";
    }
}
