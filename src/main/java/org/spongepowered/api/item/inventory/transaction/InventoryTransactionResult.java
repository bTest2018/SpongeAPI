/*
 * This file is part of SpongeAPI, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.api.item.inventory.transaction;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.util.ResettableBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * An interface for data returned by inventory operations which encapsulates the
 * result of an attempted operation.
 */
public final class InventoryTransactionResult {

    /**
     * Begin building a new InventoryTransactionResult.
     *
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Returns a builder which indicates that the transaction succeeded, but the
     * transaction result was no-op.
     *
     * @return A new transaction result
     */
    public static InventoryTransactionResult successNoTransactions() {
        return InventoryTransactionResult.builder().type(Type.SUCCESS).build();
    }

    /**
     * Returns a builder which indicates that the transaction failed, and the
     * transaction result was no-op.
     *
     * @return A new transaction result
     */
    public static InventoryTransactionResult failNoTransactions() {
        return InventoryTransactionResult.builder().type(Type.ERROR).build();
    }

    /**
     * The type of InventoryTransactionResult.
     */
    public enum Type {

        /**
         * The inventory operation succeeded.
         *
         * <p>The state of the inventory may have changed.</p>
         */
        SUCCESS,

        /**
         * The inventory operation failed for an <em>expected</em> reason (such
         * as the inventory being full, not accepting items of a supplied type
         * or a third party fully or partially canceling the transactions.
         *
         * <p>The state of the inventory may have changed.</p>
         */
        FAILURE,

        /**
         * The inventory operation failed because an <em>unexpected</em>
         * condition occurred.
         *
         * <p>The state of the inventory is undefined.</p>
         */
        ERROR

    }

    private final List<SlotTransaction> slotTransactions;
    private final List<ItemStackSnapshot> rejected;

    final Type type;

    InventoryTransactionResult(Builder builder) {
        this.type = checkNotNull(builder.resultType, "Result type");
        this.rejected = builder.rejected != null ? ImmutableList.copyOf(builder.rejected) : Collections.emptyList();
        this.slotTransactions = builder.slotTransactions != null ? ImmutableList.copyOf(builder.slotTransactions) : Collections.emptyList();
    }

    /**
     * Combines two transaction-results into one. All slot-transactions and rejected items are combined.
     * The resulting type is the first of this list to occur: {@link Type#ERROR}, {@link Type#FAILURE}, {@link Type#SUCCESS}
     *
     * @param other The other transaction-result.
     * @return The combined transaction-result.
     */
    public InventoryTransactionResult and(InventoryTransactionResult other) {
        Type resultType = Type.SUCCESS;
        if (this.type == Type.ERROR || other.type == Type.ERROR) {
            resultType = Type.ERROR;
        }
        if (this.type == Type.FAILURE || other.type == Type.FAILURE) {
            resultType = Type.FAILURE;
        }
        return builder().type(resultType).reject(this.rejected).reject(other.rejected)
                .transaction(this.slotTransactions).transaction(other.slotTransactions)
                .build();
    }

    /**
     * Reverts all SlotTransactions from this transaction-result
     */
    public void revert() {
        for (SlotTransaction transaction : Lists.reverse(this.slotTransactions)) {
            transaction.getSlot().set(transaction.getOriginal().createStack());
        }
    }

    /**
     * Reverts all SlotTransactions from this transaction-result if it was a {@link Type#FAILURE}
     */
    public void revertOnFailure() {
        if (this.type == Type.FAILURE) {
            this.revert();
        }
    }

    /**
     * Gets the type of result.
     *
     * @return the type of result
     */
    public Type getType() {
        return this.type;
    }

    /**
     * If items were supplied to the operation, this collection will return any
     * items which were rejected by the target inventory.
     *
     * @return any items which were rejected as part of the inventory operation
     */
    public Collection<ItemStackSnapshot> getRejectedItems() {
        return this.rejected;
    }

    /**
     * If the operation replaced items in the inventory, this collection returns
     * the ItemStacks which were replaced.
     *
     * @return any items which were ejected as part of the inventory operation
     */
    public List<SlotTransaction> getSlotTransactions() {
        return this.slotTransactions;
    }

    public static final class Builder implements ResettableBuilder<InventoryTransactionResult, Builder> {

        @Nullable Type resultType;
        @Nullable List<ItemStackSnapshot> rejected;
        @Nullable List<SlotTransaction> slotTransactions;

        Builder() {}

        /**
         * Sets the {@link Type} of transaction result being built.
         *
         * @param type The type of transaction result
         * @return This builder, for chaining
         */
        public Builder type(final Type type) {
            this.resultType = checkNotNull(type, "Type cannot be null!");
            return this;
        }

        /**
         * Adds the provided {@link ItemStack itemstacks} as stacks that have been
         * "rejected".
         *
         * @param itemStacks The itemstacks being rejected
         * @return This builder, for chaining
         */
        public Builder reject(ItemStack... itemStacks) {
            if (this.rejected == null) {
                this.rejected = new ArrayList<>();
            }
            for (ItemStack itemStack1 : itemStacks) {
                if (!itemStack1.isEmpty()) {
                    this.rejected.add(itemStack1.createSnapshot());
                }
            }
            return this;
        }

        /**
         * Adds the provided {@link ItemStack itemstacks} as stacks that have been
         * "rejected".
         *
         * @param itemStacks The itemstacks being rejected
         * @return This builder, for chaining
         */
        public Builder reject(List<ItemStackSnapshot> itemStacks) {
            if (this.rejected == null) {
                this.rejected = new ArrayList<>();
            }
            for (ItemStackSnapshot itemStack1 : itemStacks) {
                if (!itemStack1.isEmpty()) {
                    this.rejected.add(itemStack1);
                }
            }
            return this;
        }


        /**
         * Adds the provided {@link ItemStack itemstacks} as stacks that are
         * being replaced.
         *
         * @param slotTransactions The slotTransactions
         * @return This builder, for chaining
         */
        public Builder transaction(SlotTransaction... slotTransactions) {
            return this.transaction(Arrays.asList(slotTransactions));
        }

        /**
         * Adds the provided {@link ItemStack itemstacks} as stacks that are
         * being replaced.
         *
         * @param slotTransactions The slotTransactions
         * @return This builder, for chaining
         */
        public Builder transaction(List<SlotTransaction> slotTransactions) {
            if (this.slotTransactions == null) {
                this.slotTransactions = new ArrayList<>();
            }
            this.slotTransactions.addAll(slotTransactions);
            return this;
        }

        /**
         * Creates a new {@link InventoryTransactionResult}.
         *
         * @return A new inventory transaction result
         */
        public InventoryTransactionResult build() {
            checkState(this.resultType != null, "ResultType cannot be null!");
            return new InventoryTransactionResult(this);
        }

        @Override
        public Builder from(InventoryTransactionResult value) {
            checkNotNull(value, "InventoryTransactionResult cannot be null!");
            this.resultType = checkNotNull(value.type, "ResultType cannot be null!");
            this.slotTransactions = new ArrayList<>(value.getSlotTransactions());
            this.rejected = new ArrayList<>(value.getRejectedItems());
            return this;
        }

        @Override
        public Builder reset() {
            this.resultType = null;
            this.rejected = null;
            this.slotTransactions = null;
            return this;
        }

    }
}
