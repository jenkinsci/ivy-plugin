window.addEventListener("DOMContentLoaded", () => {
    document.querySelector(".ivy-perform-build").addEventListener("click", (event) => {
        const dataHolder = document.querySelector(".ivy-data-holder");
        const isParametrized = dataHolder.dataset.isParametrized === "true";

        if (!isParametrized) {
            event.preventDefault();
            const buildScheduledMessage = dataHolder.dataset.buildScheduledMessage;
            const anchor = event.target;
            fetch(anchor.href, {
                method: "post",
                headers: crumb.wrap({}),
            }).then((rsp) => {
                if (rsp.ok) {
                    notificationBar.show(buildScheduledMessage, notificationBar.SUCCESS);
                } else {
                    notificationBar.show("Could not schedule a build", notificationBar.ERROR);
                }
            });
        }
    });
});
