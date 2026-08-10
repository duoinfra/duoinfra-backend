package com.duoinfra.backend.container.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "physical_servers")
public class PhysicalServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;
    private String name;

    private int totalCpu;
    private int availableCpu;
    private int totalMemory;
    private int availableMemory;

    private PhysicalServer(String name, int totalCpu, int totalMemory) {
        this.name = name;
        this.totalCpu = totalCpu;
        this.availableCpu =  totalCpu;
        this.totalMemory = totalMemory;
        this.availableMemory = totalMemory;
    }

    protected PhysicalServer(){}

    public static PhysicalServer create(String name, int totalCpu, int totalMemory) {
        return new PhysicalServer(name, totalCpu, totalMemory);
    }

    public void allocate(int cpu, int memory) {
        // 1. 자원이 부족한지 체크
        if (availableCpu < cpu || availableMemory < memory) { // ① 조건 체크: 남은 게 요청보다 적으면
            // 2. 부족하면 예외 던지기
            throw ResourceAllocationConflictException.insufficientResource(id); // ② 안 되면 예외
        }
        // 3. 충분하면 availableCpu, availableMemory 깎기
        this.availableCpu -= cpu;
        this.availableMemory -= memory;
    }

    public void release(int cpu, int memory) {
        this.availableCpu += cpu;
        this.availableMemory += memory;
    }

    // getter

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public int getTotalCpu() {
        return totalCpu;
    }

    public int getAvailableCpu() {
        return availableCpu;
    }

    public int getTotalMemory() {
        return totalMemory;
    }

    public int getAvailableMemory() {
        return availableMemory;
    }
}
