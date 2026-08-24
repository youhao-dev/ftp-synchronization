package cn.you.ftpsynchronization.view;

import cn.you.ftpsynchronization.AppContext;
import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.pojo.FileInfo;
import cn.you.ftpsynchronization.service.PathRulesService;
import cn.you.ftpsynchronization.utils.TemplateResolver;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 新增、编辑和查看FTP传输规则窗口。页面支持上传/下载方向选择，并实时展示时间解析、本地目录和FTP目录示例。
 */
public class AddController {
    private static final DateTimeFormatter PREVIEW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_DELAY_MINUTES = 52_560_000L; //最多允许偏移约 100 年，避免极端输入导致日期溢出
    @FXML
    private Label dialogTitle;
    @FXML
    private Label dialogDescription;
    @FXML
    private Label ruleId;
    @FXML
    private TextField name;
    @FXML
    private TextField filename;
    @FXML
    private ComboBox<TransferDirection> transferType;
    @FXML
    private TextField basePath;
    @FXML
    private TextField rulePath;
    @FXML
    private TextField remoteBasePath;
    @FXML
    private TextField delayTime;
    @FXML
    private TextField latestFileCount;
    @FXML
    private Button chooseBasePathButton;
    @FXML
    private Button operate;
    @FXML
    private Label legacyNotice;
    @FXML
    private CheckBox legacyCompatibility;
    @FXML
    private Label previewNote;
    @FXML
    private TableView<PreviewRow> previewTable;
    @FXML
    private TableColumn<PreviewRow, String> previewTimeColumn;
    @FXML
    private TableColumn<PreviewRow, String> previewLocalColumn;
    @FXML
    private TableColumn<PreviewRow, String> previewRemoteColumn;
    private final PathRulesService pathRulesService = AppContext.getInstance().pathRulesService();
    private Mode mode = Mode.ADD;
    private Runnable dataChangeListener;

    /**
     * 初始化预览表格和实时输入监听。
     */
    @FXML
    private void initialize() {
        transferType.getItems().setAll(TransferDirection.values());
        transferType.getSelectionModel().select(TransferDirection.UPLOAD);
        transferType.valueProperty().addListener((observable,oldValue,newValue) -> refreshPreview());
        previewTimeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().dateTime()));
        previewLocalColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().localPath()));
        previewRemoteColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().remotePath()));
        previewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (TextField field : List.of(basePath, rulePath, remoteBasePath, delayTime))
            field.textProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
        legacyCompatibility.selectedProperty().addListener((observable, oldValue, newValue) -> refreshPreview());
    }

    /**
     * 初始化表单模式及规则数据。
     */
    public void initData(Mode mode, FileInfo fileInfo, Runnable dataChangeListener) {
        this.mode = mode == null ? Mode.ADD : mode;
        this.dataChangeListener = dataChangeListener;
        boolean legacy = fileInfo != null && fileInfo.usesLegacyTemplate();
        if (fileInfo != null) {
            ruleId.setText(nvl(fileInfo.getId()));
            name.setText(nvl(fileInfo.getName()));
            filename.setText(nvl(fileInfo.getFilename()));
            transferType.getSelectionModel().select(fileInfo.isDownload()?TransferDirection.DOWNLOAD:TransferDirection.UPLOAD);
            basePath.setText(nvl(fileInfo.getBasePath()));
            rulePath.setText(nvl(fileInfo.getRulePath()));
            remoteBasePath.setText(nvl(fileInfo.getRemoteBasePath()));
            delayTime.setText(String.valueOf(fileInfo.getDelayTime()));
            latestFileCount.setText(fileInfo.getLatestFileCount() == null ? "" : fileInfo.getLatestFileCount().toString());
        } else {
            ruleId.setText("");
            transferType.getSelectionModel().select(TransferDirection.UPLOAD);
            delayTime.setText("0");
            latestFileCount.setText("");
        }
        legacyCompatibility.setSelected(legacy);
        legacyCompatibility.setVisible(legacy);
        legacyCompatibility.setManaged(legacy);
        legacyNotice.setVisible(legacy);
        legacyNotice.setManaged(legacy);
        applyMode();
        refreshPreview();
    }

    /**
     * 选择本地根目录。
     */
    @FXML
    private void chooseBasePath(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.text("button.chooseDirectory"));
        File current = new File(basePath.getText().trim());
        if (current.isDirectory()) chooser.setInitialDirectory(current);
        File selected = chooser.showDialog(((Node) event.getSource()).getScene().getWindow());
        if (selected != null) basePath.setText(selected.getAbsolutePath());
    }

    /**
     * 保存新增或修改的规则。
     */
    @FXML
    private void save(ActionEvent event) {
        try {
            FileInfo fileInfo = buildAndValidate();
            if (mode == Mode.ADD) pathRulesService.addPath(fileInfo);
            else if (mode == Mode.EDIT) pathRulesService.updatePath(fileInfo);
            if (dataChangeListener != null) dataChangeListener.run();
            close(event);
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
        } catch (Exception e) {
            showWarning(I18n.text("alert.saveFailed")+": "+safeMessage(e));
        }
    }

    /**
     * 关闭当前规则窗口。
     */
    @FXML
    private void close(ActionEvent event) {
        ((Stage) ((Node) event.getSource()).getScene().getWindow()).close();
    }

    /**
     * 根据当前输入构建规则并执行基础校验。
     */
    private FileInfo buildAndValidate() {
        String nameText=text(name),basePathText=text(basePath),rulePathText=text(rulePath),remoteText=text(remoteBasePath),delayText=text(delayTime);
        String type=transferType.getValue()==TransferDirection.DOWNLOAD?FileInfo.TRANSFER_DOWNLOAD:FileInfo.TRANSFER_UPLOAD;
        if (nameText.isBlank()) throw new IllegalArgumentException(I18n.text("validation.ruleName"));
        if (basePathText.isBlank()) throw new IllegalArgumentException(I18n.text("validation.localRoot"));
        if (rulePathText.isBlank()) throw new IllegalArgumentException(I18n.text("validation.rulePath"));
        if (remoteText.isBlank()) throw new IllegalArgumentException(I18n.text("validation.remoteRoot"));
        if (rulePathText.contains("..")) throw new IllegalArgumentException(I18n.text("validation.pathEscape"));
        long delay = parseDelay(delayText, true);
        Integer number = number(latestFileCount);
        if (number != null && number < 1) throw new IllegalArgumentException(I18n.text("validation.fileCount"));
        int templateVersion = legacyCompatibility.isManaged() && legacyCompatibility.isSelected() ? 1 : 2;
        return new FileInfo().setId(ruleId.getText()).setName(nameText).setFilename(text(filename))
                .setBasePath(basePathText).setRulePath(rulePathText).setRemoteBasePath(remoteText)
                .setDelayTime(delay).setTemplateVersion(templateVersion).setLatestFileCount(number).setTransferType(type);
    }

    /**
     * 应用新增、编辑或只读查看模式。
     */
    private void applyMode() {
        boolean view = mode == Mode.VIEW;
        dialogTitle.setText(I18n.text(mode==Mode.ADD?"dialog.addRule":mode==Mode.EDIT?"dialog.editRule":"dialog.viewRule"));
        dialogDescription.setText(I18n.text(view?"dialog.viewDescription":"dialog.description"));
        for (TextField field : List.of(name, filename, basePath, rulePath, remoteBasePath, delayTime, latestFileCount))
            field.setEditable(!view);
        chooseBasePathButton.setVisible(!view);
        chooseBasePathButton.setManaged(!view);
        operate.setVisible(!view);
        operate.setManaged(!view);
        transferType.setDisable(view);
        legacyCompatibility.setDisable(view);
        operate.setText(I18n.text(mode == Mode.ADD ? "button.createRule" : "button.saveChanges"));
    }

    /**
     * 根据当前表单实时生成三组“日期时间 -> 本地目录 -> FTP目录”示例。
     */
    private void refreshPreview() {
        if (previewTable == null) return;
        long delay = parseDelay(text(delayTime), false);
        boolean legacy = legacyCompatibility != null && legacyCompatibility.isManaged() && legacyCompatibility.isSelected();
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> bases = List.of(now, now.plusHours(1), now.plusDays(1));
        List<PreviewRow> rows = bases.stream().map(base -> {
            LocalDateTime resolved = base.plusMinutes(delay);
            String dynamic = TemplateResolver.resolve(text(rulePath), resolved, legacy);
            return new PreviewRow(resolved.format(PREVIEW_TIME), emptyAsDash(TemplateResolver.joinLocalPreview(text(basePath), dynamic)), emptyAsDash(TemplateResolver.joinRemote(text(remoteBasePath), dynamic)));
        }).toList();
        previewTable.getItems().setAll(rows);
        String direction=I18n.text(transferType.getValue()==TransferDirection.DOWNLOAD?"direction.download":"direction.upload");
        previewNote.setText(I18n.text("preview.summary",direction,formatDelay(delay),I18n.text(legacy?"preview.legacy":"preview.modern")));
    }

    /**
     * 解析时间偏移；预览模式输入非法时暂按 0 处理，保存模式则抛出提示。
     */
    private long parseDelay(String value, boolean strict) {
        if (value == null || value.isBlank()) return 0;
        try {
            long delay = Long.parseLong(value.trim());
            if (delay < -MAX_DELAY_MINUTES || delay > MAX_DELAY_MINUTES) {
                if (strict) throw new IllegalArgumentException(I18n.text("validation.offsetRange",MAX_DELAY_MINUTES));
                return 0;
            }
            return delay;
        } catch (NumberFormatException e) {
            if (strict) throw new IllegalArgumentException(I18n.text("validation.offsetInteger"));
            return 0;
        }
    }

    /**
     * 格式化时间偏移提示。
     */
    private String formatDelay(long delay) {
        return delay == 0 ? I18n.text("offset.zero") : delay > 0 ? I18n.text("offset.future",delay) : I18n.text("offset.past",delay);
    }

    /**
     * 获取文本框去空格值。
     */
    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * 获取文本框去空格后转换的数字
     */
    private Integer number(TextField field) {
        if(field == null || field.getText() == null) return null;
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(I18n.text("validation.integerSimple"));
        }
    }

    /**
     * null 字符串转空字符串。
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空字符串显示为短横线。
     */
    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /**
     * 获取异常安全信息。
     */
    private String safeMessage(Exception e) {
        return e == null ? I18n.text("common.unknown") : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /**
     * 显示输入提示。
     */
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18n.text("common.warning"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 规则窗口模式。
     */
    public enum Mode {ADD, EDIT, VIEW}

    /** 规则方向使用稳定代码，显示名称由当前语言决定。 */
    private enum TransferDirection {
        UPLOAD("direction.upload"), DOWNLOAD("direction.download");
        private final String key;
        /** 创建带本地化资源键的传输方向。 */
        TransferDirection(String key) { this.key=key; }
        /** 获取当前语言的方向名称。 */
        @Override public String toString() { return I18n.text(key); }
    }

    /**
     * 规则实时预览行。
     */
    public record PreviewRow(String dateTime, String localPath, String remotePath) {
    }
}
