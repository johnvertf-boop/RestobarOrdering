package com.scc.model;

public record User (int id, String username, String password, String roles, String status){
    @Override
    public String toString(){
        return String.format("[id] %s (%s) STATUS: %s", id, username, roles, status);
    }
}