plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2.x"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements {
        // Minecraft 1.21.11 renamed ResourceLocation -> Identifier
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}
