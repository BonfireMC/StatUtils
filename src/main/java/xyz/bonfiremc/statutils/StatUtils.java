package xyz.bonfiremc.statutils;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class StatUtils implements ModInitializer {
    private static final SuggestionProvider<ServerCommandSource> SUGGEST_STAT_TYPES = (ctx, builder) -> {
        Registries.STAT_TYPE.getKeys().forEach(id -> builder.suggest(id.getValue().toString()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_STATS = (ctx, builder) -> {
        Identifier typeId = IdentifierArgumentType.getIdentifier(ctx, "type");
        StatType<?> type = Registries.STAT_TYPE.get(typeId);

        if (type != null) {
            type.iterator().forEachRemaining(stat -> builder.suggest(stat.getValue().toString()));
        }

        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            dispatcher.register(literal("stats")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(argument("targets", EntityArgumentType.players())
                            .then(argument("type", IdentifierArgumentType.identifier())
                                    .suggests(SUGGEST_STAT_TYPES)
                                    .then(argument("stat", IdentifierArgumentType.identifier())
                                            .suggests(SUGGEST_STATS)
                                            .then(argument("value", IntegerArgumentType.integer()).executes(this::setStat))
                                    )
                            )
                    )
            );
        });
    }

    private int setStat(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(ctx, "targets");
        Identifier statTypeId = IdentifierArgumentType.getIdentifier(ctx, "type");
        Identifier statId = IdentifierArgumentType.getIdentifier(ctx, "stat");
        int value = IntegerArgumentType.getInteger(ctx, "value");

        @SuppressWarnings("unchecked")
        StatType<Object> type = (StatType<Object>) Registries.STAT_TYPE.get(statTypeId);

        if (type == null) {
            ctx.getSource().sendError(Text.translatable("commands.stats.failed.invalid_stat_type", statTypeId.toString()));
            return 0;
        }

        Registry<Object> registry = type.getRegistry();

        if (!registry.containsId(statId)) {
            ctx.getSource().sendError(Text.translatable("commands.stats.failed.invalid_stat", statId.toString(), statTypeId.toString()));
            return 0;
        }

        Object key = registry.get(statId);
        Stat<Object> stat = type.getOrCreateStat(key);

        for (ServerPlayerEntity player : targets) {
            player.getStatHandler().setStat(player, stat, value);
        }

        String statTranslationKey = "stat." + stat.getValue().toString().replace(":", ".");

        ctx.getSource().sendFeedback(
                () -> Text.translatable("commands.stats.success", Text.translatableWithFallback(statTranslationKey, stat.getName()), value, targets.size()),
                false
        );

        return 1;
    }
}
