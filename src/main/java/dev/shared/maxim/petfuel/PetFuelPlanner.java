package dev.shared.maxim.petfuel;

public final class PetFuelPlanner {

    public static final double PET_FUEL_URI_COST = 0.25D;

    private PetFuelPlanner() {
    }

    public static Plan plan(double currentFuel, int minFuel, int buyAmount, int minUridiumReserve, double availableUridium) {
        if (currentFuel > minFuel) {
            return new Plan(Action.SKIP_ENOUGH_FUEL, 0, 0D);
        }

        double cost = buyAmount * PET_FUEL_URI_COST;
        if (availableUridium - cost < minUridiumReserve) {
            return new Plan(Action.SKIP_NOT_ENOUGH_URI, 0, cost);
        }

        return new Plan(Action.BUY, buyAmount, cost);
    }

    public enum Action {
        BUY,
        SKIP_ENOUGH_FUEL,
        SKIP_NOT_ENOUGH_URI
    }

    public static final class Plan {
        public final Action action;
        public final int amountToBuy;
        public final double uridiumCost;

        private Plan(Action action, int amountToBuy, double uridiumCost) {
            this.action = action;
            this.amountToBuy = amountToBuy;
            this.uridiumCost = uridiumCost;
        }
    }
}
