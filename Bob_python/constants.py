import os
import threading

import keyboard

from Bob.detector.concrete.object_detect_yolov5 import ObjectDetector
from Bob.detector.concrete.face_detect_deepface import FaceDetector
from Bob.dbctrl.concrete.crt_database import JSONDatabase
import base64
import json
from typing import List, Optional
from Bob.communication.concrete.crt_package import StringPackage, Base64LinePackage
from Bob.communication.framework.fw_listener import PackageListener
from Bob.communication.framework.fw_package_device import PackageDevice
from Bob.detector.framework.detector import DetectListener
from Bob.robot.concrete.crt_command import DynamixelVelocityCommand
from command_utils import getCommandsFromFileName
from device_config import getRobot

obj_db_location = f"db{os.path.sep}objects.json"
face_db_location = f"db{os.path.sep}faces.json"
stories_db_location = f"db{os.path.sep}stories.json"
db_charset = 'UTF-8'

object_db = JSONDatabase(open(obj_db_location, encoding=db_charset))
face_db = JSONDatabase(open(face_db_location, encoding=db_charset))
stories_db = JSONDatabase(open(stories_db_location, encoding=db_charset))

detector = None
monitor = None

robot = getRobot()
robot.open()


def stop():
    robot.doCommand(DynamixelVelocityCommand(11, 0))
    robot.doCommand(DynamixelVelocityCommand(12, 0))

    robot.doCommand(DynamixelVelocityCommand(13, 0))
    robot.doCommand(DynamixelVelocityCommand(14, 0))


def walk(direction: int):
    if direction == 1:
        robot.doCommand(DynamixelVelocityCommand(11, 10000))
        robot.doCommand(DynamixelVelocityCommand(12, 10000))

        robot.doCommand(DynamixelVelocityCommand(13, -10000))
        robot.doCommand(DynamixelVelocityCommand(14, -10000))
        print("walk forward")
    elif direction == 2:
        robot.doCommand(DynamixelVelocityCommand(11, -10000))
        robot.doCommand(DynamixelVelocityCommand(12, -10000))

        robot.doCommand(DynamixelVelocityCommand(13, 10000))
        robot.doCommand(DynamixelVelocityCommand(14, 10000))
        print("walk backward")
    elif direction == 3:
        robot.doCommand(DynamixelVelocityCommand(11, 10000))
        robot.doCommand(DynamixelVelocityCommand(12, 10000))

        robot.doCommand(DynamixelVelocityCommand(13, -2000))
        robot.doCommand(DynamixelVelocityCommand(14, -2000))
        print("turn right")
    elif direction == 4:
        robot.doCommand(DynamixelVelocityCommand(11, 2000))
        robot.doCommand(DynamixelVelocityCommand(12, 2000))

        robot.doCommand(DynamixelVelocityCommand(13, -10000))
        robot.doCommand(DynamixelVelocityCommand(14, -10000))
        print("turn left")


def keyboard_ctl():
    robot.enableAllServos(True)
    keyboard.on_press_key("w", lambda _: walk(1))
    keyboard.on_press_key("s", lambda _: walk(2))
    keyboard.on_press_key("d", lambda _: walk(3))
    keyboard.on_press_key("a", lambda _: walk(4))
    keyboard.on_press_key(" ", lambda _: stop())
    while True:
        pass


th = threading.Thread(target=keyboard_ctl)
th.start()


def formatDataToJsonString(id: int, type: str, content: str, data):
    sendData = {"id": id, "response_type": type, "content": content,
                "data": data}
    return json.dumps(sendData, ensure_ascii=False)


def doAction(action):
    print("do action:", action)
    robot.doCommands(getCommandsFromFileName(action))


class CommandControlListener(PackageListener):
    def __init__(self, device: PackageDevice):
        self.__id_counter = 0
        self.package_device = device
        self.mode: str = ""

    def onReceive(self, data: bytes):
        global detector
        d = base64.decodebytes(data)
        cmd = d.decode()
        print("receive:", cmd)

        if cmd == "DETECT_OBJECT":
            if detector is not None:
                detector.stop()

            detector = ObjectDetector(ObjectDetectListener(self.package_device))
            detector.start()
            self.mode = cmd

        elif cmd == "DETECT_FACE":
            if detector is not None:
                detector.stop()

            detector = FaceDetector(FaceDetectListener(self.package_device))
            detector.start()
            self.mode = cmd
        elif cmd == "DETECT_INTER_OBJECT":
            if detector is not None:
                detector.stop()

            detector = ObjectDetector(InteractiveObjectDetectListener(self.package_device))
            detector.start()
            self.mode = cmd

        elif cmd == "START_DETECT":
            print("Start detect")
            if detector is None:
                return
            detector.resume()
        elif cmd == "PAUSE_DETECT":
            if detector is None:
                return
            print("Pause detect")
            detector.pause()
        elif cmd == "STOP_DETECT":
            if detector is not None:
                detector.stop()

        elif cmd == "DB_GET_ALL":
            all_data: json = object_db.getAllData()
            sendData = {"id": self.__id_counter, "response_type": "json_object", "content": "all_object",
                        "data": all_data}
            jsonString = json.dumps(sendData, ensure_ascii=False)
            print("Send:", jsonString)
            self.package_device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))
            self.__id_counter = self.__id_counter + 1

        elif cmd.startswith("STORY_GET"):
            l1 = cmd[10:]
            if l1 == "LIST":
                print("list all")
                stories_list = []
                all_data: json = stories_db.getAllData()
                for story in all_data:
                    stories_list.append(
                        {"id": story['id'], "name": (story['data']['name']), "total": (story['data']['total'])})

                jsonString = formatDataToJsonString(0, "json_array", "all_stories_info", stories_list)
                print("Send:", jsonString)
                self.package_device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))
            elif l1.startswith("STORY"):
                story_id = l1[6:]
                print("get story", story_id)
                story_content = stories_db.queryForId(story_id)
                jsonString = formatDataToJsonString(0, "json_object", "story_content", story_content['data'])
                print("Send:", jsonString)
                self.package_device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))
        elif cmd.startswith("DO_ACTION"):
            action = cmd[10:]
            threading.Thread(target=doAction, args=(action,)).start()
            # doAction(action)
        elif cmd == "STOP_ALL_ACTION":
            robot.stopAllAction()


class FaceDetectListener(DetectListener):
    def __init__(self, device: PackageDevice):
        self.device = device

    def onDetect(self, face_type: str):
        print("now face emotion: " + face_type)
        obj: Optional[json] = face_db.queryForId(face_type)
        if obj is not None:
            data: json = obj['data']
            sendData = {"id": -1, "response_type": "json_object", "content": "single_object", "data": data}
            jsonString = json.dumps(sendData, ensure_ascii=False)
            print("Send:", jsonString)
            self.device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))


class ObjectDetectListener(DetectListener):
    def __init__(self, device: PackageDevice):
        self.device = device

    def onDetect(self, objectList: List):
        for dobj in objectList:
            if dobj['confidence'] < 0.65:
                continue

            obj: Optional[json] = object_db.queryForId(dobj['name'])
            if obj is not None:
                data: json = obj['data']
                sendData = {"id": -1, "response_type": "json_object", "content": "single_object", "data": data}
                jsonString = json.dumps(sendData, ensure_ascii=False)
                print("Send:", jsonString)
                self.device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))


class InteractiveObjectDetectListener(DetectListener):
    def __init__(self, device: PackageDevice):
        self.device = device

    def onDetect(self, objectList: List):
        for dobj in objectList:
            if dobj['confidence'] < 0.65:
                continue

            obj: Optional[json] = object_db.queryForId(dobj['name'])
            if obj is not None:
                data: json = obj['data']
                sendData = {"id": -1, "response_type": "json_object", "content": "single_object", "data": data}
                jsonString = json.dumps(sendData, ensure_ascii=False)
                print("Send:", jsonString)
                self.device.writePackage(Base64LinePackage(StringPackage(jsonString, "UTF-8")))
