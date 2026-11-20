
package com.example.e_commerce;

public class User {

    private int id;
    private String fullName;
    private String email;
    private String avatar;
    private byte[] avatarImageBytes;
    private String phone;
    private String address;

    public User() {
        this.avatar = "👤";
        this.phone = "";
        this.address = "";
    }

    public User(int id, String fullName, String email, String avatar) {
        this(id, fullName, email, avatar, "", "");
    }

    public User(int id, String fullName, String email, String avatar, String phone, String address) {
        this(id, fullName, email, avatar, phone, address, null);
    }

    public User(int id, String fullName, String email, String avatar, String phone, String address,
            byte[] avatarImageBytes) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.avatar = (avatar != null && !avatar.isEmpty()) ? avatar : "👤";
        this.phone = (phone != null) ? phone : "";
        this.address = (address != null) ? address : "";
        this.avatarImageBytes = avatarImageBytes == null ? null : avatarImageBytes.clone();
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

    public String getAvatar() {
        return avatar != null && !avatar.isEmpty() ? avatar : "👤";
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public byte[] getAvatarImageBytes() {
        return avatarImageBytes == null ? null : avatarImageBytes.clone();
    }

    public void setAvatarImageBytes(byte[] avatarImageBytes) {
        this.avatarImageBytes = avatarImageBytes == null ? null : avatarImageBytes.clone();
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
