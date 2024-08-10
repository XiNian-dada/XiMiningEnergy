package cn.hairuosky.ximiningenergy;

public class HealingPotion {
    private String name;
    private int customModelData;
    private String type;
    private int amount;

    public HealingPotion(String name, int customModelData, String type, int amount) {
        this.name = name;
        this.customModelData = customModelData;
        this.type = type;
        this.amount = amount;
    }

    public String getName() {
        return name;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "HealingPotion{" +
                "name='" + name + '\'' +
                ", customModelData=" + customModelData +
                ", type='" + type + '\'' +
                ", amount=" + amount +
                '}';
    }
}