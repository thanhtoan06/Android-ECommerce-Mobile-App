
package com.example.e_commerce;

public class User {

    private int id;
    private String fullName;
    private String email;
    private String password;
    private String avatar;
    private String phone;
    private String address;

    public User() {
        this.avatar = "👤";
        this.phone = "";
        this.address = "";
    }

    public User(int id, String fullName, String email, String password, String avatar) {
        this(id, fullName, email, password, avatar, "", "");
    }

    public User(int id, String fullName, String email, String password, String avatar, String phone, String address) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
        this.phone = (phone != null) ? phone : "";
        this.address = (address != null) ? address : "";
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAvatar() {
        return avatar != null && !avatar.isEmpty() ? avatar : "👤";
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = (phone != null) ? phone : "";
    }

    public String getAddress() {
        return address != null ? address : "";
    }

    public void setAddress(String address) {
        this.address = (address != null) ? address : "";
    }
}
