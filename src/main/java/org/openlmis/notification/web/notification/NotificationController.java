/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.notification.web.notification;

import java.util.List;
import java.util.stream.Collectors;
import org.openlmis.notification.domain.Notification;
import org.openlmis.notification.repository.NotificationRepository;
import org.openlmis.notification.service.NotificationHandler;
import org.openlmis.notification.service.PermissionService;
import org.openlmis.notification.util.Pagination;
import org.openlmis.notification.web.ValidationException;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NotificationController {

  private static final XLogger XLOGGER = XLoggerFactory.getXLogger(NotificationController.class);

  @Autowired
  private NotificationDtoValidator notificationValidator;

  @Autowired
  private PermissionService permissionService;

  @Autowired
  private NotificationHandler notificationHandler;
  
  @Autowired
  private NotificationRepository notificationRepository;

  @InitBinder
  private void initBinder(WebDataBinder binder) {
    binder.setValidator(notificationValidator);
  }

  /**
   * Send an email notification.
   *
   * @param notificationDto details of the message
   */
  @PostMapping("/notifications")
  @ResponseStatus(HttpStatus.OK)
  public void sendNotification(@RequestBody @Validated NotificationDto notificationDto,
      BindingResult bindingResult) {
    XLOGGER.entry(notificationDto);
    Profiler profiler = new Profiler("SEND_NOTIFICATION");
    profiler.setLogger(XLOGGER);

    profiler.start("CHECK_PERMISSION");
    permissionService.canSendNotification();

    if (bindingResult.getErrorCount() > 0) {
      FieldError fieldError = bindingResult.getFieldError();
      throw new ValidationException(fieldError.getDefaultMessage(), fieldError.getField());
    }

    profiler.start("IMPORT_FROM_DTO");
    Notification notification = Notification.newInstance(notificationDto);

    profiler.start("SAVE_NOTIFICATION");
    notification = notificationRepository.save(notification);

    profiler.start("HANDLE_NOTIFICATION");
    notificationHandler.handle(notification);

    profiler.stop().log();
    XLOGGER.exit();
  }

  /**
   * Get notifications.
   */
  @GetMapping("/notifications")
  @ResponseStatus(HttpStatus.OK)
  public Page<NotificationDto> getNotificationCollection(Pageable pageable) {
    XLOGGER.entry(pageable);
    Profiler profiler = new Profiler("GET_NOTIFICATIONS");
    profiler.setLogger(XLOGGER);

    profiler.start("FIND_ALL");
    Page<Notification> notificationsPage = notificationRepository.findAll(pageable);

    profiler.start("CREATE_DTOS");
    List<NotificationDto> notificationDtos = notificationsPage.getContent().stream()
        .map(this::exportToDto)
        .collect(Collectors.toList());

    profiler.start("CREATE_PAGE");
    Page<NotificationDto> notificationDtosPage = Pagination.getPage(notificationDtos, pageable,
        notificationsPage.getTotalElements());

    profiler.stop().log();
    XLOGGER.exit(notificationDtosPage);
    return notificationDtosPage;
  }

  private NotificationDto exportToDto(Notification notification) {
    NotificationDto dto = new NotificationDto();
    notification.export(dto);
    return dto;
  }
}
