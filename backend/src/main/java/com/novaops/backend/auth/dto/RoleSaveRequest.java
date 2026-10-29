package com.novaops.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RoleSaveRequest(
    @NotBlank(message = "角色名称不能为空") @Size(max = 100) String name,
    @NotNull @Size(max = 255) String description,
    @NotNull @Size(max = 50) List<@NotBlank String> permissions) {}
