package xyz.bonfiremc.statutils;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Iterator;

public class StatUtils implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(CommandManager.literal("stats")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(CommandManager.argument("targets", EntityArgumentType.players())
                            .then(CommandManager.literal("set")
                                    .then(CommandManager.argument("type", IdentifierArgumentType.identifier())
                                            .suggests((ctx, builder) -> {
                                                Registries.STAT_TYPE.getKeys().forEach(key -> {
                                                    builder.suggest(key.getValue().toString());
                                                });
                                                return builder.buildFuture();
                                            })
                                            .then(CommandManager.argument("stat", IdentifierArgumentType.identifier())
                                                    .suggests((ctx, builder) -> {
                                                        Identifier typeId = IdentifierArgumentType.getIdentifier(ctx, "type");
                                                        StatType<?> type = Registries.STAT_TYPE.get(typeId);

                                                        if (type != null) {
                                                            Iterator<? extends Stat<?>> iterator = type.iterator();

                                                            while (iterator.hasNext()) {
                                                                Stat<?> stat = iterator.next();
                                                                builder.suggest(stat.getValue().toString());
                                                            }
                                                        }

                                                        return builder.buildFuture();
                                                    })
                                                    .then(CommandManager.argument("value", IntegerArgumentType.integer())
                                                            .executes((ctx) -> {
                                                                Collection<ServerPlayerEntity> players = EntityArgumentType.getPlayers(ctx, "targets");

                                                                @SuppressWarnings("unchecked")
                                                                StatType<Object> type = (StatType<Object>) Registries.STAT_TYPE.get(IdentifierArgumentType.getIdentifier(ctx, "type"));

                                                                if (type != null) {
                                                                    Object item = type.getRegistry().get(IdentifierArgumentType.getIdentifier(ctx, "stat"));
                                                                    Stat<Object> stat = type.getOrCreateStat(item);

                                                                    for (ServerPlayerEntity player : players) {
                                                                        player.getStatHandler().setStat(player, stat, IntegerArgumentType.getInteger(ctx, "value"));
                                                                    }
                                                                }

                                                                return 1;
                                                            })
                                                    )
                                            )
                                    )
                            )
                    )
            );
        });
    }
}
