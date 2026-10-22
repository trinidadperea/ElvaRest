document.addEventListener("DOMContentLoaded", () => {
    const botonesTema = document.querySelectorAll("[data-bs-theme-value]");
    const iconosTema = document.querySelectorAll("[data-lte-theme-icon]");

    function aplicarTema(tema) {
        const temaSistemaOscuro = window.matchMedia(
            "(prefers-color-scheme: dark)"
        ).matches;

        const temaFinal = tema === "auto"
            ? (temaSistemaOscuro ? "dark" : "light")
            : tema;

        // Aplicar el tema a toda la página
        document.documentElement.setAttribute("data-bs-theme", temaFinal);

        // Actualizar los iconos
        iconosTema.forEach(icono => {
            icono.classList.toggle(
                "d-none",
                icono.dataset.lteThemeIcon !== temaFinal
            );
        });

        // Marcar la opción seleccionada
        botonesTema.forEach(boton => {
            const activo = boton.dataset.bsThemeValue === tema;

            boton.classList.toggle("active", activo);

            if (activo) {
                boton.setAttribute("aria-pressed", "true");
            } else {
                boton.setAttribute("aria-pressed", "false");
            }
        });

        localStorage.setItem("tema", tema);
    }

    // Recuperar el tema guardado o usar el modo automático
    const temaGuardado = localStorage.getItem("tema") || "auto";
    aplicarTema(temaGuardado);

    // Cambiar el tema cuando se selecciona una opción
    botonesTema.forEach(boton => {
        boton.addEventListener("click", () => {
            aplicarTema(boton.dataset.bsThemeValue);
        });
    });

    // Actualizar automáticamente si cambia el tema del sistema
    window.matchMedia("(prefers-color-scheme: dark)")
        .addEventListener("change", () => {
            if ((localStorage.getItem("tema") || "auto") === "auto") {
                aplicarTema("auto");
            }
        });
});