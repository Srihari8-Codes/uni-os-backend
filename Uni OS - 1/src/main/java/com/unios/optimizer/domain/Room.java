package com.unios.optimizer.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    private String id;
    private String name;
    private int capacity;
}
