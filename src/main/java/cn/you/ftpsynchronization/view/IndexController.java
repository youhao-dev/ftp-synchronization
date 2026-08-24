package cn.you.ftpsynchronization.view;

import cn.you.ftpsynchronization.AppContext;
import cn.you.ftpsynchronization.FxStart;
import cn.you.ftpsynchronization.config.AppSettings;
import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.i18n.SupportedLanguage;
import cn.you.ftpsynchronization.log.InMemoryLogStore;
import cn.you.ftpsynchronization.pojo.FileInfo;
import cn.you.ftpsynchronization.pojo.FtpLogEntry;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.pojo.SystemLogEntry;
import cn.you.ftpsynchronization.pojo.TransferStatus;
import cn.you.ftpsynchronization.service.PathRulesService;
import cn.you.ftpsynchronization.stack.FtpSynchronizationStack;
import cn.you.ftpsynchronization.utils.FtpUtil;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Rectangle2D;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主窗口控制器：管理FTP传输规则、系统传输记录、FTP文件记录和运行配置四个页面。
 */
public class IndexController {
    /** 主窗口可恢复的导航页面。 */
    public enum MainPage { RULES, SYSTEM_LOGS, FTP_LOGS, SETTINGS }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOGS = 200;

    //页面与导航
    @FXML
    private VBox rulesPage;
    @FXML
    private VBox systemLogPage;
    @FXML
    private VBox ftpLogPage;
    @FXML
    private VBox settingsPage;
    @FXML
    private Button navRulesButton;
    @FXML
    private Button navSystemButton;
    @FXML
    private Button navFtpButton;
    @FXML
    private Button navSettingsButton;

    //规则页面
    @FXML
    private TableView<FileInfo> rulesTable;
    @FXML
    private TableColumn<FileInfo, String> nameColumn;
    @FXML
    private TableColumn<FileInfo, String> transferTypeColumn;
    @FXML
    private TableColumn<FileInfo, String> filenameColumn;
    @FXML
    private TableColumn<FileInfo, String> basePathColumn;
    @FXML
    private TableColumn<FileInfo, String> rulePathColumn;
    @FXML
    private TableColumn<FileInfo, String> remoteBasePathColumn;
    @FXML
    private TableColumn<FileInfo, Number> delayTimeColumn;
    @FXML
    private TableColumn<FileInfo, Number> latestFileCountColumn;
    @FXML
    private TableColumn<FileInfo, Boolean> statusColumn;
    @FXML
    private TableColumn<FileInfo, Void> actionColumn;
    @FXML
    private Label ruleCountLabel;
    @FXML
    private Label scheduleLabel;
    @FXML
    private Label lastRunLabel;
    @FXML
    private Label poolStatusLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label runningBadge;
    @FXML
    private Label versionLabel;

    //系统记录页面
    @FXML
    private TableView<SystemLogEntry> systemLogTable;
    @FXML
    private TableColumn<SystemLogEntry, String> systemTimeColumn;
    @FXML
    private TableColumn<SystemLogEntry, String> systemLevelColumn;
    @FXML
    private TableColumn<SystemLogEntry, String> systemMessageColumn;

    //FTP记录页面
    @FXML
    private TableView<FtpLogEntry> ftpLogTable;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpTimeColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpRuleColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpFileColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpStatusColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpSizeColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpDurationColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpRemoteColumn;
    @FXML
    private TableColumn<FtpLogEntry, String> ftpMessageColumn;

    //设置页面
    @FXML
    private TextField ftpHostField;
    @FXML
    private TextField ftpPortField;
    @FXML
    private TextField ftpUsernameField;
    @FXML
    private PasswordField ftpPasswordField;
    @FXML
    private TextField connectTimeoutField;
    @FXML
    private TextField readTimeoutField;
    @FXML
    private TextField dataTimeoutField;
    @FXML
    private TextField poolSizeField;
    @FXML
    private TextField poolBorrowTimeoutField;
    @FXML
    private TextField frequencyValueField;
    @FXML
    private ComboBox<FrequencyUnit> frequencyUnitCombo;
    @FXML
    private ComboBox<SupportedLanguage> languageCombo;
    @FXML
    private TextField scheduleSecondField;
    @FXML
    private TextField latestFileCountField;
    @FXML
    private TextField fileStableSecondsField;
    @FXML
    private Label settingsFileLabel;
    @FXML
    private Label settingsStatusLabel;
    @FXML
    private Button testConnectionButton;

    private final AppContext appContext = AppContext.getInstance();
    private final PathRulesService pathRulesService = appContext.pathRulesService();
    private final FtpSynchronizationStack uploadStack = appContext.uploadStack();
    private final InMemoryLogStore logStore = appContext.logStore();
    private final ObservableList<SystemLogEntry> systemLogItems = FXCollections.observableArrayList();
    private final ObservableList<FtpLogEntry> ftpLogItems = FXCollections.observableArrayList();
    private final ConcurrentLinkedQueue<SystemLogEntry> pendingSystemLogs = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<FtpLogEntry> pendingFtpLogs = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean systemFlushScheduled = new AtomicBoolean(false);
    private final AtomicBoolean ftpFlushScheduled = new AtomicBoolean(false);
    private final List<Runnable> unsubscribeActions = new java.util.ArrayList<>();
    private MainPage currentPage=MainPage.RULES;

    /**
     * 初始化所有页面、实时日志监听和设置表单。
     */
    @FXML
    private void initialize() {
        initRulesTable();
        initSystemLogTable();
        initFtpLogTable();
        initSettings();
        String version=IndexController.class.getPackage().getImplementationVersion();
        versionLabel.setText(version==null?"dev":"v"+version);
        unsubscribeActions.add(uploadStack.addListener(event -> Platform.runLater(() -> updateStatus(event))));
        unsubscribeActions.add(logStore.addSystemListener(this::enqueueSystemLog));
        unsubscribeActions.add(logStore.addFtpListener(this::enqueueFtpLog));
        systemLogItems.setAll(logStore.systemSnapshot());
        ftpLogItems.setAll(logStore.ftpSnapshot());
        systemLogTable.setItems(systemLogItems);
        ftpLogTable.setItems(ftpLogItems);
        showRulesPage(null);
        refreshData();
    }

    /**
     * 初始化规则表格列、操作列和双击编辑。
     */
    private void initRulesTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(nvl(data.getValue().getName())));
        transferTypeColumn.setCellValueFactory(data -> new SimpleStringProperty(I18n.text(data.getValue().isDownload()?"direction.download":"direction.upload")));
        filenameColumn.setCellValueFactory(data -> new SimpleStringProperty(nvl(data.getValue().getFilename())));
        basePathColumn.setCellValueFactory(data -> new SimpleStringProperty(nvl(data.getValue().getBasePath())));
        rulePathColumn.setCellValueFactory(data -> new SimpleStringProperty(nvl(data.getValue().getRulePath())));
        remoteBasePathColumn.setCellValueFactory(data -> new SimpleStringProperty(nvl(data.getValue().getRemoteBasePath())));
        delayTimeColumn.setCellValueFactory(data -> new SimpleLongProperty(data.getValue().getDelayTime()));
        latestFileCountColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getLatestFileCount()));
        rulesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        rulesTable.setPlaceholder(new Label(I18n.text("placeholder.noRules")));
        rulesTable.setRowFactory(table -> {
            var row = new TableRow<FileInfo>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openRuleDialog(AddController.Mode.EDIT, row.getItem());
            });
            return row;
        });
        initStatusColumn();
        initActionColumn();
    }

    /**
     * 初始化系统传输记录表格。
     */
    private void initSystemLogTable() {
        systemTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().time().format(TIME_FORMAT)));
        systemLevelColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().level()));
        systemMessageColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message().render()));
        systemLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        systemLogTable.setPlaceholder(new Label(I18n.text("placeholder.noSystemLogs")));
        systemLevelColumn.setCellFactory(column -> new TableCell<>() {
            /** 根据日志级别设置轻量样式。 */
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("log-info", "log-warn", "log-error");
                if (!empty && item != null)
                    getStyleClass().add("ERROR".equals(item) ? "log-error" : "WARN".equals(item) ? "log-warn" : "log-info");
            }
        });
    }

    /**
     * 初始化 FTP 文件传输记录表格。
     */
    private void initFtpLogTable() {
        ftpTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().time().format(TIME_FORMAT)));
        ftpRuleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ruleName()));
        ftpFileColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fileName()));
        ftpStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(localizedFtpStatus(data.getValue().status())));
        ftpSizeColumn.setCellValueFactory(data -> new SimpleStringProperty(formatBytes(data.getValue().fileSize())));
        ftpDurationColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().durationMs() + " ms"));
        ftpRemoteColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().remotePath()));
        ftpMessageColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message().render()));
        ftpLogTable.setPlaceholder(new Label(I18n.text("placeholder.noFtpLogs")));
        ftpStatusColumn.setCellFactory(column -> new TableCell<>() {
            /** 根据 FTP 处理状态设置样式。 */
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("ftp-success", "ftp-skip", "ftp-fail");
                if (!empty && item != null)
                    getStyleClass().add(I18n.text("result.success").equals(item) ? "ftp-success" : I18n.text("result.failed").equals(item) ? "ftp-fail" : "ftp-skip");
            }
        });
    }

    /**
     * 初始化设置页面并载入已持久化配置。
     */
    private void initSettings() {
        frequencyUnitCombo.setItems(FXCollections.observableArrayList(FrequencyUnit.values()));
        languageCombo.setItems(FXCollections.observableArrayList(SupportedLanguage.values()));
        languageCombo.setValue(I18n.language());
        languageCombo.valueProperty().addListener((observable,oldValue,newValue) -> changeLanguage(newValue));
        loadSettingsToForm();
        settingsFileLabel.setText(appContext.config().settingsFile().toString());
    }

    /** 保存语言并重新加载主场景，后台同步组件保持原实例运行。 */
    private void changeLanguage(SupportedLanguage selected) {
        if (selected==null||selected==I18n.language()) return;
        appContext.config().saveLanguage(selected.code());
        I18n.configure(selected.code());
        FxStart.reloadMainView();
    }

    /**
     * 切换到传输规则页面。
     */
    @FXML
    private void showRulesPage(ActionEvent event) {
        currentPage=MainPage.RULES;
        showPage(rulesPage, navRulesButton);
        refreshData();
    }

    /**
     * 切换到系统传输记录页面。
     */
    @FXML
    private void showSystemLogPage(ActionEvent event) {
        currentPage=MainPage.SYSTEM_LOGS;
        showPage(systemLogPage, navSystemButton);
    }

    /**
     * 切换到 FTP 文件记录页面。
     */
    @FXML
    private void showFtpLogPage(ActionEvent event) {
        currentPage=MainPage.FTP_LOGS;
        showPage(ftpLogPage, navFtpButton);
    }

    /**
     * 切换到运行配置页面。
     */
    @FXML
    private void showSettingsPage(ActionEvent event) {
        currentPage=MainPage.SETTINGS;
        loadSettingsToForm();
        showPage(settingsPage, navSettingsButton);
    }

    /** 获取当前导航页，供语言重载前保存界面状态。 */
    public MainPage currentPage() { return currentPage; }

    /** 在语言重载后恢复原导航页面。 */
    public void restorePage(MainPage page) {
        switch (page==null?MainPage.RULES:page) {
            case RULES -> showRulesPage(null);
            case SYSTEM_LOGS -> showSystemLogPage(null);
            case FTP_LOGS -> showFtpLogPage(null);
            case SETTINGS -> showSettingsPage(null);
        }
    }

    /**
     * 切换主内容页并同步导航高亮。
     */
    private void showPage(VBox target, Button activeButton) {
        for (VBox page : List.of(rulesPage, systemLogPage, ftpLogPage, settingsPage)) {
            boolean show = page == target;
            page.setVisible(show);
            page.setManaged(show);
        }
        for (Button button : List.of(navRulesButton, navSystemButton, navFtpButton, navSettingsButton))
            button.getStyleClass().remove("nav-button-active");
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active"))
            activeButton.getStyleClass().add("nav-button-active");
    }

    /**
     * 新增传输规则。
     */
    @FXML
    private void addItem(ActionEvent event) {
        openRuleDialog(AddController.Mode.ADD, null);
    }

    /**
     * 手动刷新规则列表。
     */
    @FXML
    private void flushed(ActionEvent event) {
        refreshData();
        statusLabel.setText(I18n.text("status.rulesRefreshed"));
    }

    /**
     * 立即执行一轮文件传输检查。
     */
    @FXML
    private void runNow(ActionEvent event) {
        uploadStack.triggerNow();
    }

    /**
     * 清空全部规则，执行前二次确认。
     */
    @FXML
    private void clearRules(ActionEvent event) {
        if (pathRulesService.getAllPath().isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.text("alert.clearRulesMessage"), ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(I18n.text("alert.clearRulesTitle"));
        alert.setHeaderText(I18n.text("alert.clearRulesHeader"));
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            pathRulesService.clear();
            refreshData();
            logStore.warn(LocalizedMessage.localized("log.rulesCleared"));
        }
    }

    /**
     * 清空内存中的系统传输记录。
     */
    @FXML
    private void clearSystemLogs(ActionEvent event) {
        logStore.clearSystem();
        pendingSystemLogs.clear();
        systemLogItems.clear();
        statusLabel.setText(I18n.text("status.systemCleared"));
    }

    /**
     * 清空内存中的 FTP 文件记录。
     */
    @FXML
    private void clearFtpLogs(ActionEvent event) {
        logStore.clearFtp();
        pendingFtpLogs.clear();
        ftpLogItems.clear();
        statusLabel.setText(I18n.text("status.ftpCleared"));
    }

    /**
     * 保存 FTP、连接池和文件传输频率设置并立即生效。
     */
    @FXML
    private void saveSettings(ActionEvent event) {
        try {
            AppSettings settings = buildSettingsFromForm();
            appContext.config().save(settings);
            appContext.settingsChanged();
            settingsStatusLabel.setText(I18n.text("status.saved"));
            statusLabel.setText(I18n.text("status.configSaved"));
            refreshData();
            showInfo(I18n.text("alert.saveSuccessTitle"), I18n.text("alert.saveSuccess"));
        } catch (Exception e) {
            showWarning(I18n.text("alert.saveFailed"), safeMessage(e));
        }
    }

    /**
     * 使用当前页面输入值异步测试 FTP，避免网络请求阻塞 JavaFX 界面线程。
     */
    @FXML
    private void testConnection(ActionEvent event) {
        final AppSettings settings;
        try {
            settings = buildSettingsFromForm();
        } catch (Exception e) {
            showWarning(I18n.text("alert.invalidConfig"), safeMessage(e));
            return;
        }

        testConnectionButton.setDisable(true);
        testConnectionButton.setText(I18n.text("status.connecting"));
        settingsStatusLabel.setText(I18n.text("status.connecting"));

        Thread.ofVirtual().name("ftp-test").start(() -> {
            try {
                String result = FtpUtil.testConnection(settings.ftpHost(), settings.ftpPort(), settings.ftpUsername(), settings.ftpPassword(), settings.connectTimeoutMs(), settings.readTimeoutMs(), settings.dataTimeoutMs());
                logStore.info(LocalizedMessage.localized("log.connectionTestSuccess",settings.ftpHost(),settings.ftpPort()));

                Platform.runLater(() -> {
                    testConnectionButton.setDisable(false);
                    testConnectionButton.setText(I18n.text("button.testConnection"));
                    settingsStatusLabel.setText(result);
                    showInfo(I18n.text("alert.connectionSuccess"), I18n.text("alert.connectionSuccess")+"\n\n"+settings.ftpHost()+":"+settings.ftpPort()+"\n"+settings.ftpUsername());
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                logStore.error(LocalizedMessage.localized("log.connectionTestFailed",message));

                Platform.runLater(() -> {
                    testConnectionButton.setDisable(false);
                    testConnectionButton.setText(I18n.text("button.testConnection"));
                    settingsStatusLabel.setText(I18n.text("status.connectionFailed",message));
                    showWarning(I18n.text("alert.connectionFailed"), message);
                });
            }
        });
    }

    /**
     * 初始化规则状态列。
     */
    private void initStatusColumn() {
        statusColumn.setCellValueFactory(data -> new SimpleBooleanProperty(data.getValue().getIsRunning()));

        statusColumn.setCellFactory(column -> new TableCell<>() {
            {
                setOnMouseClicked(e -> {
                    e.consume();
                    if (e.getButton() != MouseButton.PRIMARY) return;
                    FileInfo rule = currentItem();
                    if (!isEmpty() && rule != null) toggleRuleStatus(rule);
                });
            }

            /** 更新状态单元格。 */
            @Override
            protected void updateItem(Boolean running, boolean empty) {
                super.updateItem(running, empty);
                getStyleClass().removeAll("status-running", "status-stopped");

                if (empty || running == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }

                setText(I18n.text(running ? "status.running" : "status.stopped"));
                getStyleClass().add(running ? "status-running" : "status-stopped");
                setTooltip(new Tooltip(I18n.text(running ? "tooltip.stopRule" : "tooltip.startRule")));
            }

            /** 获取当前行规则。 */
            private FileInfo currentItem() {
                return getIndex() >= 0 && getIndex() < getTableView().getItems().size() ? getTableView().getItems().get(getIndex()) : null;
            }
        });
    }

    /**
     * 重新加载规则表格和顶部运行统计。
     */
    public void refreshData() {
        List<FileInfo> data = pathRulesService.getAllPath().values().stream().sorted(Comparator.comparing(item -> nvl(item.getName()), String.CASE_INSENSITIVE_ORDER)).toList();
        rulesTable.setItems(FXCollections.observableArrayList(data));
        ruleCountLabel.setText(String.valueOf(data.size()));
        scheduleLabel.setText(uploadStack.scheduleDescription());
        lastRunLabel.setText(uploadStack.getLastFinishedTime() == null ? I18n.text("status.notRun") : uploadStack.getLastFinishedTime().format(TIME_FORMAT));
        poolStatusLabel.setText(appContext.ftpPool().totalCount() + " / " + appContext.config().ftpPoolSize());
        runningBadge.setText(I18n.text(uploadStack.isRunning() ? "status.running" : "status.waiting"));
        runningBadge.getStyleClass().removeAll("badge-running", "badge-idle");
        runningBadge.getStyleClass().add(uploadStack.isRunning() ? "badge-running" : "badge-idle");
    }

    /**
     * 切换规则运行状态。
     */
    private void toggleRuleStatus(FileInfo rule) {
        if (rule == null) return;

        if (rule.getIsRunning()) {
            rule.setIsRunning(false);
            ruleStatusChanged(rule);
        } else {
            rule.setIsRunning(true);
            ruleStatusChanged(rule);
        }

        rulesTable.refresh();
    }

    /** 持久化规则运行状态并记录对应上传/下载日志。 */
    private void ruleStatusChanged(FileInfo rule) {
        pathRulesService.updatePath(rule);
        refreshData();
        LocalizedMessage type=LocalizedMessage.localized(rule.isDownload()?"direction.download":"direction.upload");
        if (rule.getIsRunning()) logStore.warn(LocalizedMessage.localized("log.ruleStarted",type,nvl(rule.getName())));
        else logStore.info(LocalizedMessage.localized("log.ruleStopped",type,nvl(rule.getName())));
    }

    /**
     * 创建规则表格操作列。
     */
    private void initActionColumn() {
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button viewButton = createButton(I18n.text("button.view"), "table-btn");
            private final Button editButton = createButton(I18n.text("button.edit"), "table-btn", "table-btn-primary");
            private final Button deleteButton = createButton(I18n.text("button.delete"), "table-btn", "table-btn-danger");
            private final HBox box = new HBox(7, viewButton, editButton, deleteButton);

            {
                box.setAlignment(Pos.CENTER);
                viewButton.setOnAction(e -> openRuleDialog(AddController.Mode.VIEW, currentItem()));
                editButton.setOnAction(e -> openRuleDialog(AddController.Mode.EDIT, currentItem()));
                deleteButton.setOnAction(e -> deleteRule(currentItem()));
            }

            /** 更新操作单元格。 */
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }

            /** 获取当前行规则。 */
            private FileInfo currentItem() {
                return getIndex() >= 0 && getIndex() < getTableView().getItems().size() ? getTableView().getItems().get(getIndex()) : null;
            }
        });
    }
    /**
     * 打开可滚动、可调整大小的新增/编辑/查看规则窗口，避免小屏幕内容被截断。
     */
    private void openRuleDialog(AddController.Mode mode, FileInfo fileInfo) {
        try {
            FXMLLoader loader = new FXMLLoader(FxStart.class.getResource("/fxml/add.fxml"),I18n.bundle());
            Parent root = loader.load();
            AddController controller = loader.getController();
            controller.initData(mode, fileInfo, this::refreshData);
            Stage owner = (Stage) rulesTable.getScene().getWindow();
            Screen screen = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()).stream().findFirst().orElse(Screen.getPrimary());
            Rectangle2D visual = screen.getVisualBounds();
            double width = Math.max(600, Math.min(920, visual.getWidth() - 80)), height = Math.max(520, Math.min(860, visual.getHeight() - 80));
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(FxStart.class.getResource("/css/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(owner);
            stage.setResizable(true);
            stage.setMinWidth(Math.min(720, width));
            stage.setMinHeight(Math.min(560, height));
            stage.setTitle(I18n.text(mode == AddController.Mode.ADD ? "dialog.addRule" : mode == AddController.Mode.EDIT ? "dialog.editRule" : "dialog.viewRule"));
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            showError(I18n.text("alert.openRuleFailed"), e);
        }
    }

    /**
     * 删除指定规则。
     */
    private void deleteRule(FileInfo fileInfo) {
        if (fileInfo == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.text("alert.deleteRuleMessage",nvl(fileInfo.getName())), ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(I18n.text("alert.deleteRuleTitle"));
        alert.setHeaderText(null);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            pathRulesService.deletePathById(fileInfo.getId());
            refreshData();
            LocalizedMessage type=LocalizedMessage.localized(fileInfo.isDownload()?"direction.download":"direction.upload");
            logStore.warn(LocalizedMessage.localized("log.ruleDeleted",type,nvl(fileInfo.getName())));
        }
    }

    /**
     * 处理调度器状态事件并刷新顶部状态。
     */
    private void updateStatus(FtpSynchronizationStack.UploadEvent event) {
        statusLabel.setText(event.time().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + event.message().render());
        refreshData();
    }

    /**
     * 把持久化配置加载到设置表单。
     */
    private void loadSettingsToForm() {
        if (ftpHostField == null) return;
        AppSettings s = appContext.config().snapshot();
        ftpHostField.setText(s.ftpHost());
        ftpPortField.setText(String.valueOf(s.ftpPort()));
        ftpUsernameField.setText(s.ftpUsername());
        ftpPasswordField.setText(s.ftpPassword());
        connectTimeoutField.setText(String.valueOf(s.connectTimeoutMs()));
        readTimeoutField.setText(String.valueOf(s.readTimeoutMs()));
        dataTimeoutField.setText(String.valueOf(s.dataTimeoutMs()));
        poolSizeField.setText(String.valueOf(s.ftpPoolSize()));
        poolBorrowTimeoutField.setText(String.valueOf(s.poolBorrowTimeoutMs()));
        applyFrequencyToForm(s.uploadIntervalSeconds());
        scheduleSecondField.setText(String.valueOf(s.scheduleSecond()));
        latestFileCountField.setText(String.valueOf(s.latestFileCount()));
        fileStableSecondsField.setText(String.valueOf(s.fileStableSeconds()));
        settingsStatusLabel.setText(I18n.text("settings.saveHint"));
    }

    /**
     * 从设置表单构建并校验配置对象。
     */
    private AppSettings buildSettingsFromForm() {
        String host = text(ftpHostField), username = text(ftpUsernameField), password = ftpPasswordField.getText() == null ? "" : ftpPasswordField.getText();
        if (host.isBlank()) throw new IllegalArgumentException(I18n.text("validation.ftpHost"));
        int port = parseInt(ftpPortField, I18n.text("field.ftpPort"), 1, 65535), connect = parseInt(connectTimeoutField, I18n.text("field.connectTimeout"), 1_000, 120_000), read = parseInt(readTimeoutField, I18n.text("field.readTimeout"), 1_000, 300_000), data = parseInt(dataTimeoutField, I18n.text("field.dataTimeout"), 1_000, 300_000);
        int poolSize = parseInt(poolSizeField, I18n.text("field.poolSize"), 1, 16), borrow = parseInt(poolBorrowTimeoutField, I18n.text("field.poolWait"), 1_000, 120_000);
        int interval = frequencySeconds(), scheduleSecond = parseInt(scheduleSecondField, I18n.text("field.alignSecond"), 0, 59), latest = parseInt(latestFileCountField, I18n.text("field.defaultCount"), 1, 100), stable = parseInt(fileStableSecondsField, I18n.text("field.stableSeconds"), 0, 3_600);
        return new AppSettings(host, port, username, password, connect, read, data, poolSize, borrow, interval, scheduleSecond, latest, stable, I18n.language().code());
    }

    /**
     * 根据秒数选择最直观的频率单位并回填表单。
     */
    private void applyFrequencyToForm(int seconds) {
        if (seconds % 3600 == 0) {
            frequencyValueField.setText(String.valueOf(seconds / 3600));
            frequencyUnitCombo.setValue(FrequencyUnit.HOURS);
        } else if (seconds % 60 == 0) {
            frequencyValueField.setText(String.valueOf(seconds / 60));
            frequencyUnitCombo.setValue(FrequencyUnit.MINUTES);
        } else {
            frequencyValueField.setText(String.valueOf(seconds));
            frequencyUnitCombo.setValue(FrequencyUnit.SECONDS);
        }
    }

    /**
     * 把“数值 + 秒/分钟/小时”转换为秒并限制在 5 秒~24 小时。
     */
    private int frequencySeconds() {
        int value = parseInt(frequencyValueField, I18n.text("field.frequency"), 1, 86_400);
        FrequencyUnit unit = frequencyUnitCombo.getValue() == null ? FrequencyUnit.SECONDS : frequencyUnitCombo.getValue();
        long seconds = (long) value * unit.multiplier;
        if (seconds < 5 || seconds > 86_400) throw new IllegalArgumentException(I18n.text("validation.frequency"));
        return (int) seconds;
    }

    /**
     * 解析有范围限制的整数文本框。
     */
    private int parseInt(TextField field, String name, int min, int max) {
        String value = text(field);
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new IllegalArgumentException(I18n.text("validation.range",name,min,max));
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(I18n.text("validation.integer",name));
        }
    }

    /**
     * 把系统日志加入待刷新队列，并合并为一次 JavaFX runLater，避免高频日志挤满 UI 事件队列。
     */
    private void enqueueSystemLog(SystemLogEntry entry) {
        pendingSystemLogs.add(entry);
        if (systemFlushScheduled.compareAndSet(false, true)) Platform.runLater(this::flushSystemLogs);
    }

    /**
     * 把 FTP 日志加入待刷新队列，并合并为一次 JavaFX runLater。
     */
    private void enqueueFtpLog(FtpLogEntry entry) {
        pendingFtpLogs.add(entry);
        if (ftpFlushScheduled.compareAndSet(false, true)) Platform.runLater(this::flushFtpLogs);
    }

    /**
     * 批量刷新待处理系统日志。
     */
    private void flushSystemLogs() {
        SystemLogEntry entry;
        while ((entry = pendingSystemLogs.poll()) != null) prependCapped(systemLogItems, entry);
        systemFlushScheduled.set(false);
        if (!pendingSystemLogs.isEmpty() && systemFlushScheduled.compareAndSet(false, true))
            Platform.runLater(this::flushSystemLogs);
    }

    /**
     * 批量刷新待处理 FTP 日志。
     */
    private void flushFtpLogs() {
        FtpLogEntry entry;
        while ((entry = pendingFtpLogs.poll()) != null) prependCapped(ftpLogItems, entry);
        ftpFlushScheduled.set(false);
        if (!pendingFtpLogs.isEmpty() && ftpFlushScheduled.compareAndSet(false, true))
            Platform.runLater(this::flushFtpLogs);
    }

    /**
     * 将新日志插入列表顶部并限制最多 200 条。
     */
    private <T> void prependCapped(ObservableList<T> list, T item) {
        list.add(0, item);
        while (list.size() > MAX_LOGS) list.remove(list.size() - 1);
    }

    /**
     * 创建表格操作按钮。
     */
    private Button createButton(String text, String... styles) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styles);
        return button;
    }

    /**
     * 格式化文件大小。
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        return mb < 1024 ? String.format("%.1f MB", mb) : String.format("%.2f GB", mb / 1024.0);
    }

    /**
     * 获取文本框值。
     */
    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * null 字符串转空字符串。
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 获取异常安全信息。
     */
    private String safeMessage(Throwable e) {
        return e == null ? I18n.text("common.unknown") : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** 将旧日志中的稳定中文状态映射为当前语言。 */
    private String localizedFtpStatus(TransferStatus status) {
        if (status==null) return "";
        return I18n.text(switch (status) { case SUCCESS -> "result.success"; case FAILED -> "result.failed"; case EXISTS -> "result.exists"; });
    }

    /** 注销界面监听器，供语言重载和程序退出调用。 */
    public void dispose() {
        for (Runnable unsubscribe:unsubscribeActions) try { unsubscribe.run(); } catch (Exception ignored) {}
        unsubscribeActions.clear();
    }

    /** 频率单位使用稳定枚举，显示名称再按当前语言获取。 */
    private enum FrequencyUnit {
        SECONDS(1,"unit.seconds"), MINUTES(60,"unit.minutes"), HOURS(3600,"unit.hours");
        private final int multiplier;
        private final String key;
        /** 创建带秒数倍率和资源键的单位。 */
        FrequencyUnit(int multiplier,String key) { this.multiplier=multiplier; this.key=key; }
        /** 获取本地化单位名称。 */
        @Override public String toString() { return I18n.text(key); }
    }

    /**
     * 显示成功提示对话框。
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示错误对话框。
     */
    private void showError(String message, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.text("common.error"));
        alert.setHeaderText(message);
        alert.setContentText(safeMessage(e));
        alert.showAndWait();
    }

    /**
     * 显示普通警告对话框。
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
